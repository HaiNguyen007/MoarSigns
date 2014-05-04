package gory_moon.moarsigns.network;

import com.google.common.base.Charsets;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.FMLEmbeddedChannel;
import cpw.mods.fml.common.network.FMLOutboundHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gory_moon.moarsigns.MoarSigns;
import gory_moon.moarsigns.lib.ModInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;

import java.io.IOException;
import java.util.*;

@ChannelHandler.Sharable
public class PacketPipeline extends MessageToMessageCodec<FMLProxyPacket, AbstractPacket> {

    private EnumMap<Side, FMLEmbeddedChannel> channels;
    private LinkedList<Class<? extends AbstractPacket>> packets = new LinkedList<Class<? extends AbstractPacket>>();
    private boolean isPostInitialised = false;

    /**
     * Register your packet with the pipeline. Discriminators are automatically
     * set.
     *
     * @param clazz
     *            the class to register
     *
     * @return whether registration was successful. Failure may occur if 256
     *         packets have been registered or if the registry already contains
     *         this packet
     */
    public boolean registerPacket (Class<? extends AbstractPacket> clazz) {
        if (this.packets.size() > 256)  {
            MoarSigns.logger.error("Can't register more packets.");
            return false;
        }

        if (this.packets.contains(clazz)) {
            MoarSigns.logger.error("Can't register " + clazz + " as it is already registered.");
            return false;
        }

        if (this.isPostInitialised)  {
            MoarSigns.logger.error("Can't register packets after FMLPostInitializationEvent");
            return false;
        }

        this.packets.add(clazz);
        return true;
    }

    // In line encoding of the packet, including discriminator setting
    @Override
    protected void encode (ChannelHandlerContext ctx, AbstractPacket msg, List<Object> out) throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        Class<? extends AbstractPacket> clazz = msg.getClass();
        if (!this.packets.contains(msg.getClass()))
        {
            throw new NullPointerException("No Packet Registered for: " + msg.getClass().getCanonicalName());
        }

        byte discriminator = (byte) this.packets.indexOf(clazz);
        buffer.writeByte(discriminator);
        msg.encodeInto(ctx, buffer);
        FMLProxyPacket proxyPacket = new FMLProxyPacket(buffer.copy(), ctx.channel().attr(NetworkRegistry.FML_CHANNEL).get());
        out.add(proxyPacket);
    }

    // In line decoding and handling of the packet
    @Override
    protected void decode (ChannelHandlerContext ctx, FMLProxyPacket msg, List<Object> out) throws Exception {
        ByteBuf payload = msg.payload();
        byte discriminator = payload.readByte();
        Class<? extends AbstractPacket> clazz = this.packets.get(discriminator);
        if (clazz == null) {
            throw new NullPointerException("No packet registered for discriminator: " + discriminator);
        }

        AbstractPacket pkt = clazz.newInstance();
        pkt.decodeInto(ctx, payload.slice());

        EntityPlayer player;
        switch (FMLCommonHandler.instance().getEffectiveSide()) {
            case CLIENT:
                player = this.getClientPlayer();
                pkt.handleClientSide(player);
                break;

            case SERVER:
                INetHandler netHandler = ctx.channel().attr(NetworkRegistry.NET_HANDLER).get();
                player = ((NetHandlerPlayServer) netHandler).playerEntity;
                pkt.handleServerSide(player);
                break;

            default:
        }

        out.add(pkt);
    }

    // Method to call from FMLInitializationEvent
    public void initalise () {
        this.channels = NetworkRegistry.INSTANCE.newChannel(ModInfo.CHANNEL, this);
        registerPackets();
    }

    public void registerPackets () {
        registerPacket(PacketSignMainInfo.class);
        registerPacket(PacketSignUpdate.class);
        registerPacket(PacketOpenMoarSignsGui.class);
    }

    // Method to call from FMLPostInitializationEvent
    // Ensures that packet discriminators are common between server and client
    // by using logical sorting
    public void postInitialise () {
        if (this.isPostInitialised) {
            return;
        }

        this.isPostInitialised = true;
        Collections.sort(this.packets, new Comparator<Class<? extends AbstractPacket>>() {

            @Override
            public int compare(Class<? extends AbstractPacket> clazz1, Class<? extends AbstractPacket> clazz2) {
                int com = String.CASE_INSENSITIVE_ORDER.compare(clazz1.getCanonicalName(), clazz2.getCanonicalName());
                if (com == 0) {
                    com = clazz1.getCanonicalName().compareTo(clazz2.getCanonicalName());
                }

                return com;
            }
        });
    }

    @SideOnly(Side.CLIENT)
    private EntityPlayer getClientPlayer ()
    {
        return Minecraft.getMinecraft().thePlayer;
    }

    /**
     * Send this message to everyone.
     * <p/>
     * Adapted from CPW's code in
     * cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper
     *
     * @param message
     *            The message to send
     */
    public void sendToAll (AbstractPacket message) {
        this.channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.ALL);
        this.channels.get(Side.SERVER).writeAndFlush(message);
    }

    /**
     * Send this message to the specified player.
     * <p/>
     * Adapted from CPW's code in
     * cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper
     *
     * @param message
     *            The message to send
     * @param player
     *            The player to send it to
     */
    public void sendTo (AbstractPacket message, EntityPlayerMP player) {
        this.channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.PLAYER);
        this.channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGETARGS).set(player);
        this.channels.get(Side.SERVER).writeAndFlush(message);
    }

    /**
     * Send this message to everyone within a certain range of a point.
     * <p/>
     * Adapted from CPW's code in
     * cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper
     *
     * @param message
     *            The message to send
     * @param point
     *            The
     *            {@link cpw.mods.fml.common.network.NetworkRegistry.TargetPoint}
     *            around which to send
     */
    public void sendToAllAround (AbstractPacket message, NetworkRegistry.TargetPoint point) {
        this.channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.ALLAROUNDPOINT);
        this.channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGETARGS).set(point);
        this.channels.get(Side.SERVER).writeAndFlush(message);
    }

    /**
     * Send this message to everyone within the supplied dimension.
     * <p/>
     * Adapted from CPW's code in
     * cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper
     *
     * @param message
     *            The message to send
     * @param dimensionId
     *            The dimension id to target
     */
    public void sendToDimension (AbstractPacket message, int dimensionId) {
        this.channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.DIMENSION);
        this.channels.get(Side.SERVER).attr(FMLOutboundHandler.FML_MESSAGETARGETARGS).set(dimensionId);
        this.channels.get(Side.SERVER).writeAndFlush(message);
    }

    /**
     * Send this message to the server.
     * <p/>
     * Adapted from CPW's code in
     * cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper
     *
     * @param message
     *            The message to send
     */
    public void sendToServer (AbstractPacket message) {
        this.channels.get(Side.CLIENT).attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.TOSERVER);
        this.channels.get(Side.CLIENT).writeAndFlush(message);
    }

    /**
     * Writes a (UTF-8 encoded) String to applied buffer. Will throw IOException if String length exceeds 32767 bytes
     */
    public static void writeString(String string, ByteBuf buffer) throws IOException {
        byte[] byteArray = string.getBytes(Charsets.UTF_8);

        if (byteArray.length > 32767) {
            throw new IOException("String too big (was " + string.length() + " bytes encoded, max " + 32767 + ")");
        } else {
            buffer.writeInt(byteArray.length);
            buffer.writeBytes(byteArray);
        }
    }

    /**
     * Reads a string from this buffer. Expected parameter is maximum allowed string length. Will throw IOException if
     * string length exceeds this value!
     */
    public static String readStringFromBuffer(int size, ByteBuf buffer) throws IOException {
        int j = buffer.readInt();

        if (j > size * 4) {
            throw new IOException("The received encoded string buffer length is longer than maximum allowed (" + j + " > " + size * 4 + ")");
        } else if (j < 0) {
            throw new IOException("The received encoded string buffer length is less than zero! Weird string!");
        } else {
            byte[] bytes = new byte[j];
            buffer.readBytes(bytes);
            String s = new String(bytes, Charsets.UTF_8);

            if (s.length() > size) {
                throw new IOException("The received string length is longer than maximum allowed (" + j + " > " + size + ")");
            } else {
                return s;
            }
        }
    }
}