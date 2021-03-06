package gory_moon.moarsigns;

import gory_moon.moarsigns.blocks.ModBlocks;
import gory_moon.moarsigns.client.interfaces.GuiHandler;
import gory_moon.moarsigns.integration.IntegrationHandler;
import gory_moon.moarsigns.integration.tweaker.MineTweakerIntegration;
import gory_moon.moarsigns.items.ModItems;
import gory_moon.moarsigns.lib.Reference;
import gory_moon.moarsigns.network.PacketHandler;
import gory_moon.moarsigns.proxy.CommonProxy;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Reference.MODID, name = Reference.NAME, version = Reference.VERSION, certificateFingerprint = Reference.FINGERPRINT, acceptedMinecraftVersions = "[1.10]", guiFactory = Reference.GUI_FACTORY_CLASS, updateJSON = MoarSigns.FORGE_PROMO,
        dependencies =
                "after:BiomesOPlenty;" +
                "after:forestry;" +
                "after:natura;" +
                "after:IC2;" +
                "after:tconstruct;" +
                "after:railcraft;" +
                "after:ThermalFoundation;" +
                "after:factorization;" +
                "after:basemetals;" +
                "after:techreborn;" +
                "after:Psi;" +
                "after:roots;" +
                "after:bigreactors;" +
                "after:immersiveengineering;" +
                "after:integrateddynamics;" +
                "after:draconicevolution;" +
                "after:EnderIO;" +
                "after:randomthings;" +
                "after:JEI@[3.14.0,);" +
                "after:NotEnoughItems;" +
                "after:Waila;" +
                "after:theoneprobe;" +
                "after:tumat;" +
                "after:MineTweaker3;"
        )
public class MoarSigns {

    private static final String WAILA_PROVIDER = "gory_moon.moarsigns.integration.waila.Provider.callbackRegister";
    private static final String LINK = "https://raw.githubusercontent.com/GoryMoon/MoarSigns/master/version.json";
    static final String FORGE_PROMO = "https://raw.githubusercontent.com/GoryMoon/MoarSigns/master/version_promo.json";

    @Instance(Reference.MODID)
    public static MoarSigns instance;

    @SidedProxy(clientSide = Reference.CLIENT_PROXY, serverSide = Reference.COMMON_PROXY)
    public static CommonProxy proxy;

    public static Logger logger = LogManager.getLogger("MoarSigns");

    @EventHandler
    @SuppressWarnings("unused")
    public void preInit(FMLPreInitializationEvent event) {
        ConfigHandler.instance().loadDefaultConfig(event);
        PacketHandler.init();

        FMLInterModComms.sendMessage("VersionChecker", "addVersionCheck", LINK);
        if (FMLInterModComms.sendMessage("Waila", "register", WAILA_PROVIDER)) {
            MoarSigns.logger.info("Loaded Waila Integration");
        }

        proxy.preInit();
        ModBlocks.registerTileEntities();
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void load(FMLInitializationEvent event) {
        proxy.init();
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void modsLoaded(FMLPostInitializationEvent event) {
        new IntegrationHandler().setupSigns();
        ModItems.registerRecipes();

        if (Loader.isModLoaded("Quark") || Loader.isModLoaded("quark")) {
            MoarSigns.logger.warn("Quark is loaded, MoarSigns sign editing might not work as intended");
        }

        if (Loader.isModLoaded("MineTweaker3")) {
            MineTweakerIntegration.register();
        }
    }

    @EventHandler
    public void invalidFingerprint(FMLFingerprintViolationEvent event) {
        if (!event.isDirectory())
            FMLLog.bigWarning("%s might not be trustable, the mod has been modified by another party then the author, download a trustable version from CurseForge", event.getSource());
    }

}
