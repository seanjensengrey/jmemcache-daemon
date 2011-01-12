package com.thimbleware.jmemcached.protocol.text;

import com.thimbleware.jmemcached.CacheElement;
import com.thimbleware.jmemcached.Key;
import com.thimbleware.jmemcached.LocalCacheElement;
import com.thimbleware.jmemcached.protocol.Op;
import com.thimbleware.jmemcached.protocol.CommandMessage;
import com.thimbleware.jmemcached.protocol.SessionStatus;
import com.thimbleware.jmemcached.protocol.exceptions.InvalidProtocolStateException;
import com.thimbleware.jmemcached.protocol.exceptions.MalformedCommandException;
import com.thimbleware.jmemcached.protocol.exceptions.UnknownCommandException;
import com.thimbleware.jmemcached.util.BufferUtils;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The MemcachedCommandDecoder is responsible for taking lines from the MemcachedFrameDecoder and parsing them
 * into CommandMessage instances for handling by the MemcachedCommandHandler
 * <p/>
 * Protocol status is held in the SessionStatus instance which is shared between each of the decoders in the pipeline.
 */
public final class MemcachedCommandDecoder extends SimpleChannelUpstreamHandler {

    private SessionStatus status;

    private static final ChannelBuffer NOREPLY = ChannelBuffers.wrappedBuffer("noreply".getBytes());


    public MemcachedCommandDecoder(SessionStatus status) {
        this.status = status;
    }

    /**
     * Process an inbound string from the pipeline's downstream, and depending on the state (waiting for data or
     * processing commands), turn them into the correct type of command.
     *
     * @param channelHandlerContext netty channel handler context
     * @param messageEvent the netty event that corresponds to th emessage
     * @throws Exception
     */
    @Override
    public void messageReceived(ChannelHandlerContext channelHandlerContext, MessageEvent messageEvent) throws Exception {
        ChannelBuffer in = (ChannelBuffer) messageEvent.getMessage();

        try {
            // Because of the frame handler, we are assured that we are receiving only complete lines or payloads.
            // Verify that we are in 'processing()' mode
            if (status.state == SessionStatus.State.PROCESSING) {
                // split into pieces
                List<ChannelBuffer> pieces = new ArrayList<ChannelBuffer>(6);
                int pos = in.bytesBefore((byte) ' ');
                do {
                    if (pos != -1) {
                        ChannelBuffer slice = in.slice(in.readerIndex(), pos);
                        slice.readerIndex(0);
                        pieces.add(slice);
                        in.skipBytes(pos + 1);
                    }
                } while ((pos = in.bytesBefore((byte) ' ')) != -1);
                pieces.add(in.slice());
                in.skipBytes(in.readableBytes());

                processLine(pieces, messageEvent.getChannel(), channelHandlerContext);
            } else if (status.state == SessionStatus.State.PROCESSING_MULTILINE) {
                ChannelBuffer payload = in.copy(in.readerIndex(), in.readableBytes());
                in.skipBytes(in.readableBytes());
                continueSet(messageEvent.getChannel(), status, payload, channelHandlerContext);
            } else {
                throw new InvalidProtocolStateException("invalid protocol state");
            }

        } finally {
            // Now indicate that we need more for this command by changing the session status's state.
            // This instructs the frame decoder to start collecting data for us.
            // Note, we don't do this if we're waiting for data.
            if (status.state != SessionStatus.State.WAITING_FOR_DATA) status.ready();
        }
    }

    /**
     * Process an individual complete protocol line and either passes the command for processing by the
     * session handler, or (in the case of SET-type commands) partially parses the command and sets the session into
     * a state to wait for additional data.
     *
     * @param parts                 the (originally space separated) parts of the command
     * @param channel               the netty channel to operate on
     * @param channelHandlerContext the netty channel handler context
     * @throws com.thimbleware.jmemcached.protocol.exceptions.MalformedCommandException
     * @throws com.thimbleware.jmemcached.protocol.exceptions.UnknownCommandException
     */
    private void processLine(List<ChannelBuffer> parts, Channel channel, ChannelHandlerContext channelHandlerContext) throws UnknownCommandException, MalformedCommandException {
        final int numParts = parts.size();

        // Turn the command into an enum for matching on
        Op op;
        try {
            op = Op.FindOp(parts.get(0));
            if (op == null)
                throw new IllegalArgumentException("unknown operation: " + parts.get(0).toString());
        } catch (IllegalArgumentException e) {
            throw new UnknownCommandException("unknown operation: " + parts.get(0).toString());
        }

        // Produce the initial command message, for filling in later
        CommandMessage cmd = CommandMessage.command(op);

        switch (op) {
            case DELETE:
                cmd.setKey(parts.get(1));

                if (numParts >= 2) {
                    if (parts.get(numParts - 1).equals(NOREPLY)) {
                        cmd.noreply = true;
                        if (numParts == 4)
                            cmd.time = BufferUtils.atoi((parts.get(2)));
                    } else if (numParts == 3)
                        cmd.time = BufferUtils.atoi((parts.get(2)));
                }
                Channels.fireMessageReceived(channelHandlerContext, cmd, channel.getRemoteAddress());
                break;

            case DECR:
            case INCR:
                // Malformed
                if (numParts < 2 || numParts > 3)
                    throw new MalformedCommandException("invalid increment command");

                cmd.setKey(parts.get(1));
                cmd.incrAmount = BufferUtils.atoi(parts.get(2));

                if (numParts == 3 && parts.get(2).equals(NOREPLY)) {
                    cmd.noreply = true;
                }

                Channels.fireMessageReceived(channelHandlerContext, cmd, channel.getRemoteAddress());
                break;

            case FLUSH_ALL:
                if (numParts >= 1) {
                    if (parts.get(numParts - 1).equals(NOREPLY)) {
                        cmd.noreply = true;
                        if (numParts == 3)
                            cmd.time = BufferUtils.atoi((parts.get(1)));
                    } else if (numParts == 2)
                        cmd.time = BufferUtils.atoi((parts.get(1)));
                }
                Channels.fireMessageReceived(channelHandlerContext, cmd, channel.getRemoteAddress());
                break;
            //
            case VERBOSITY: // verbosity <time> [noreply]\r\n
                // Malformed
                if (numParts < 2 || numParts > 3)
                    throw new MalformedCommandException("invalid verbosity command");

                cmd.time = BufferUtils.atoi((parts.get(1))); // verbose level

                if (numParts > 1 && parts.get(2).equals(NOREPLY))
                    cmd.noreply = true;
                Channels.fireMessageReceived(channelHandlerContext, cmd, channel.getRemoteAddress());
                break;
            case APPEND:
            case PREPEND:
            case REPLACE:
            case ADD:
            case SET:
            case CAS:
                // if we don't have all the parts, it's malformed
                if (numParts < 5) {
                    throw new MalformedCommandException("invalid command length");
                }

                // Fill in all the elements of the command
                int size = BufferUtils.atoi(parts.get(4));
                int expire = BufferUtils.atoi(parts.get(3));
                int flags = BufferUtils.atoi(parts.get(2));
                cmd.element = new LocalCacheElement(new Key(parts.get(1).slice()), flags, expire != 0 && expire < CacheElement.THIRTY_DAYS ? LocalCacheElement.Now() + expire : expire, 0L);

                // look for cas and "noreply" elements
                if (numParts > 5) {
                    int noreply = op == Op.CAS ? 6 : 5;
                    if (op == Op.CAS) {
                        cmd.cas_key = BufferUtils.atol(parts.get(5).copy().array());
                    }

                    if (numParts == noreply + 1 && parts.get(noreply).equals(NOREPLY))
                        cmd.noreply = true;
                }

                // Now indicate that we need more for this command by changing the session status's state.
                // This instructs the frame decoder to start collecting data for us.
                status.needMore(size, cmd);
                break;

            //
            case GET:
            case GETS:
            case STATS:
            case VERSION:
            case QUIT:
                // Get all the keys
                cmd.setKeys(parts.subList(1, numParts));

                // Pass it on.
                Channels.fireMessageReceived(channelHandlerContext, cmd, channel.getRemoteAddress());
                break;
            default:
                throw new UnknownCommandException("unknown command: " + op);
        }
    }

    /**
     * Handles the continuation of a SET/ADD/REPLACE command with the data it was waiting for.
     *
     * @param channel               netty channel
     * @param state                 the current session status (unused)
     * @param remainder             the bytes picked up
     * @param channelHandlerContext netty channel handler context
     */
    private void continueSet(Channel channel, SessionStatus state, ChannelBuffer remainder, ChannelHandlerContext channelHandlerContext) {
        state.cmd.element.setData(remainder);
        Channels.fireMessageReceived(channelHandlerContext, state.cmd, channelHandlerContext.getChannel().getRemoteAddress());
    }
}
