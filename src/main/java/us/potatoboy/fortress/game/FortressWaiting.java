package us.potatoboy.fortress.game;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import us.potatoboy.fortress.game.active.FortressActive;
import us.potatoboy.fortress.game.map.FortressMap;
import us.potatoboy.fortress.game.map.FortressMapGenerator;
import xyz.nucleoid.fantasy.BubbleWorldConfig;
import xyz.nucleoid.plasmid.game.*;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.event.RequestStartListener;
import xyz.nucleoid.plasmid.game.player.GameTeam;
import xyz.nucleoid.plasmid.map.MapTickets;

import java.util.List;

public class FortressWaiting {
    private final GameSpace gameSpace;
    private final FortressMap map;
    private final FortressConfig config;
    private final TeamSelectionLobby teamSelectionLobby;
    private final ModuleManager moduleManager;

    private FortressWaiting(GameSpace gameSpace, FortressMap map, FortressConfig config, TeamSelectionLobby teamSelectionLobby, ModuleManager moduleManager) {
        this.gameSpace = gameSpace;
        this.map = map;
        this.config = config;
        this.teamSelectionLobby = teamSelectionLobby;
        this.moduleManager = moduleManager;

        gameSpace.addResource(MapTickets.acquire(gameSpace.getWorld(), map.bounds));
    }


    public static GameOpenProcedure open(GameOpenContext<FortressConfig> context) {
        FortressMapGenerator generator = new FortressMapGenerator(context.getConfig().mapConfig);
        FortressMap map = generator.create();
        ModuleManager moduleManager = new ModuleManager(context.getServer().getStructureManager());

        BubbleWorldConfig worldConfig = new BubbleWorldConfig()
                .setGenerator(map.asGenerator(context.getServer()))
                .setDefaultGameMode(GameMode.ADVENTURE)
                .setGameRule(GameRules.NATURAL_REGENERATION, false);

        return context.createOpenProcedure(worldConfig, game -> {
            GameWaitingLobby.applyTo(game, context.getConfig().playerConfig);

            List<GameTeam> teams = ImmutableList.of(FortressTeams.RED, FortressTeams.BLUE);
            TeamSelectionLobby teamSelectionLobby = TeamSelectionLobby.applyTo(game, teams);

            FortressWaiting waiting = new FortressWaiting(game.getSpace(), map, context.getConfig(), teamSelectionLobby, moduleManager);

            map.setStarterCells(FortressTeams.BLUE, "blue_start", game.getSpace().getWorld());
            map.setStarterCells(FortressTeams.RED, "red_start", game.getSpace().getWorld());

            game.on(RequestStartListener.EVENT, waiting::requestStart);
            game.on(PlayerAddListener.EVENT, waiting::addPlayer);
            game.on(PlayerDeathListener.EVENT, waiting::playerDeath);
        });
    }

    private StartResult requestStart() {
        if (gameSpace.getPlayers().size() < config.playerConfig.getMinPlayers()) {
            return StartResult.NOT_ENOUGH_PLAYERS;
        }

        Multimap<GameTeam, ServerPlayerEntity> players = HashMultimap.create();
        teamSelectionLobby.allocate(players::put);

        FortressActive.open(gameSpace, map, config, players, moduleManager);

        return StartResult.OK;
    }

    private void addPlayer(ServerPlayerEntity playerEntity) {
        spawnPlayer(playerEntity);
        giveBook(playerEntity);
    }

    private ActionResult playerDeath(ServerPlayerEntity playerEntity, DamageSource source) {
        playerEntity.setHealth(20.0F);
        spawnPlayer(playerEntity);
        return ActionResult.PASS;
    }

    private void spawnPlayer(ServerPlayerEntity player) {
        FortressSpawnLogic.resetPlayer(player, GameMode.ADVENTURE);
        FortressSpawnLogic.spawnPlayer(player, map.waitingSpawn, gameSpace.getWorld(), 0.0f);
    }

    private void giveBook(ServerPlayerEntity player) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);

        ListTag pages = new ListTag();

        pages.add(StringTag.of(Text.Serializer.toJson(new TranslatableText("text.fortress.book.page1"))));
        pages.add(StringTag.of(Text.Serializer.toJson(new TranslatableText("text.fortress.book.page2"))));

        book.getOrCreateTag().put("pages", pages);
        book.getOrCreateTag().putString("title", "How To Play");
        book.getOrCreateTag().putString("author", "Potatoboy9999");
        book.getOrCreateTag().putInt("HideFlags", 63);
        book.getOrCreateTag().putBoolean("resolved", false);

        player.inventory.insertStack(2, book);
    }
}
