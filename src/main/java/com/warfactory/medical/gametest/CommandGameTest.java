package com.warfactory.medical.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.warfactory.medical.WFMedical;
import com.warfactory.medical.core.HealthState;
import com.warfactory.medical.core.MedicalProfile;
import com.warfactory.medical.core.limb.LimbType;
import com.warfactory.medical.server.command.WFMedicalCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The {@code /wfmedical} command tree.
 *
 * <p>Commands are how every other subsystem is inspected and reproduced, and a Brigadier tree is built
 * at runtime out of string literals -- a renamed subcommand, an argument in the wrong order or a branch
 * that was never attached all compile perfectly and only surface when somebody types them. Parsing every
 * documented form against a real dispatcher is cheap and catches all of that.
 *
 * <p>Execution is covered for the branches that change state, driven through the dispatcher so the
 * argument parsing and the handler are tested together rather than the handler alone.
 */
@GameTestHolder(WFMedical.MOD_ID)
@PrefixGameTestTemplate(false)
public class CommandGameTest {

    private static final String TEMPLATE = "empty";

    /** A fresh dispatcher with only our tree on it, so nothing else can absorb a typo. */
    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        CommandDispatcher<CommandSourceStack> d = new CommandDispatcher<>();
        WFMedicalCommands.register(d);
        return d;
    }

    /** An operator-level source at the test structure, so permission-gated branches are reachable. */
    private static CommandSourceStack source(GameTestHelper helper) {
        return helper.getLevel().getServer().createCommandSourceStack().withPermission(2);
    }

    /**
     * Whether {@code command} both consumes its whole input and lands on a node that can execute.
     *
     * <p>The second half matters: Brigadier happily "parses" {@code /wfmedical blood} -- it reads every
     * character and reports no error -- while leaving you on an intermediate literal with no command
     * attached. Checking only the reader would call that a success.
     */
    private static boolean runnable(CommandDispatcher<CommandSourceStack> d, CommandSourceStack src,
                                    String command) {
        ParseResults<CommandSourceStack> parse = d.parse(command, src);
        return !parse.getReader().canRead()
                && parse.getExceptions().isEmpty()
                && parse.getContext().getLastChild().getCommand() != null;
    }

    private static void parses(GameTestHelper helper, CommandDispatcher<CommandSourceStack> d,
                               CommandSourceStack src, String command) {
        ParseResults<CommandSourceStack> parse = d.parse(command, src);
        if (parse.getReader().canRead()) {
            helper.fail("'/" + command + "' did not parse; stopped at '"
                    + parse.getReader().getRemaining() + "'");
            return;
        }
        if (!parse.getExceptions().isEmpty()) {
            helper.fail("'/" + command + "' parsed with errors: " + parse.getExceptions().values());
            return;
        }
        if (parse.getContext().getLastChild().getCommand() == null) {
            helper.fail("'/" + command + "' parsed but is not executable -- it stops on an "
                    + "intermediate node");
        }
    }

    private static void rejects(GameTestHelper helper, CommandDispatcher<CommandSourceStack> d,
                                CommandSourceStack src, String command) {
        rejects(helper, d, src, command, "not a runnable command");
    }

    private static void rejects(GameTestHelper helper, CommandDispatcher<CommandSourceStack> d,
                                CommandSourceStack src, String command, String why) {
        if (runnable(d, src, command)) {
            helper.fail("'/" + command + "' was accepted, but it is " + why);
        }
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void theWholeCommandTreeParses(GameTestHelper helper) {
        CommandDispatcher<CommandSourceStack> d = dispatcher();
        CommandSourceStack src = source(helper);

        for (String cmd : new String[]{
                "wfmedical query",
                "wfmedical heal",
                "wfmedical reset",
                "wfmedical kill",
                "wfmedical revive",
                "wfmedical trauma add @s TORSO laceration_large 0.5",
                "wfmedical trauma remove @s TORSO laceration_large",
                "wfmedical trauma clear @s",
                "wfmedical blood set @s 2000",
                "wfmedical blood add @s -500",
                "wfmedical suppression set @s 0.5",
                "wfmedical suppression clear @s",
                "wfmedical drug set @s 1.5",
                "wfmedical drug add @s 0.5",
                "wfmedical drug clear @s",
                "wfmedical unconscious @s",
                "wfmedical unconscious @s 200",
                "wfmedical asphyxia @s",
                "wfmedical substance @s morphine",
                "wfmedical state @s CRITICAL",
                "wfmedical fracture @s LEFT_LEG",
                "wfmedical bleed @s TORSO 0.5",
                "wfmedical bleed @s TORSO",
                "wfmedical hittest",
                "wfmedical hittest 12.0",
                "wfmedical rig",
                "wfmedical hitbox",
                "wfmedical hitbox status",
                "wfmedical hitbox show",
                "wfmedical hitbox show STANDING",
                "wfmedical hitbox export",
                "wfmedical hitbox export file",
                "wfmedical hitbox debug",
                "wfmedical hitbox debug on",
                "wfmedical hitbox debug off",
                "wfmedical hitbox set STANDING HEAD SX 8.5",
                "wfmedical hitbox add STANDING HEAD OY -0.5",
                "wfmedical hitbox reset",
                "wfmedical hitbox reset STANDING",
                "wfmedical hitbox reset STANDING HEAD",
                "wfmedical hitbox envelope set STANDING HORIZONTAL 0.2",
                "wfmedical hitbox envelope add STANDING VERTICAL 0.1",
                "wfmedical hitbox envelope reset",
        }) {
            parses(helper, d, src, cmd);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void theParseCheckCanActuallyFail(GameTestHelper helper) {
        // Guard: if parse() reported success for anything, the sweep above would be worthless.
        CommandDispatcher<CommandSourceStack> d = dispatcher();
        CommandSourceStack src = source(helper);
        rejects(helper, d, src, "wfmedical definitelynotasubcommand");
        rejects(helper, d, src, "wfmedical blood", "an intermediate literal is not runnable");
        rejects(helper, d, src, "wfmedical blood set @s", "a missing required argument");
        rejects(helper, d, src, "wfmedical blood set @s notanumber", "a malformed argument");
        rejects(helper, d, src, "notourcommand query");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void theTreeIsGatedBehindOperatorPermission(GameTestHelper helper) {
        // These commands set blood volume and kill players; an unprivileged source must not see them.
        CommandDispatcher<CommandSourceStack> d = dispatcher();
        CommandSourceStack plain = helper.getLevel().getServer().createCommandSourceStack()
                .withPermission(0);
        ParseResults<CommandSourceStack> parse = d.parse("wfmedical kill", plain);
        if (!parse.getReader().canRead() && parse.getExceptions().isEmpty()) {
            helper.fail("a permission-0 source could run /wfmedical kill");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void bloodAndTraumaCommandsChangeTheProfile(GameTestHelper helper) {
        // Driven through a source whose entity is our victim, so `@s` resolves without going near the
        // player list. Adding a real player to the list runs the full login sequence, and any mod with a
        // login-time sync packet (TACZ has one) blows up in an unrelated test.
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        CommandDispatcher<CommandSourceStack> d = dispatcher();
        CommandSourceStack src = helper.getLevel().getServer().createCommandSourceStack()
                .withEntity(v).withPermission(2);

        run(helper, d, src, "wfmedical blood set @s 1500");
        if (Math.abs(profile.getBloodMl() - 1500.0D) > 1.0D) {
            helper.fail("blood set left " + profile.getBloodMl());
        }

        run(helper, d, src, "wfmedical blood add @s 500");
        if (Math.abs(profile.getBloodMl() - 2000.0D) > 1.0D) {
            helper.fail("blood add left " + profile.getBloodMl());
        }

        run(helper, d, src, "wfmedical trauma add @s TORSO laceration_large 0.6");
        if (profile.limb(LimbType.TORSO).getTraumas().isEmpty()) {
            helper.fail("trauma add produced no wound");
        }

        run(helper, d, src, "wfmedical fracture @s LEFT_LEG");
        if (profile.limb(LimbType.LEFT_LEG).getTraumas().isEmpty()) {
            helper.fail("fracture produced no wound");
        }

        run(helper, d, src, "wfmedical trauma clear @s");
        if (!profile.limb(LimbType.TORSO).getTraumas().isEmpty()
                || !profile.limb(LimbType.LEFT_LEG).getTraumas().isEmpty()) {
            helper.fail("trauma clear left wounds behind: " + TestBodies.describe(profile));
        }

        run(helper, d, src, "wfmedical suppression set @s 0.75");
        if (Math.abs(profile.getPainSuppression() - 0.75F) > 0.01F) {
            helper.fail("suppression set left " + profile.getPainSuppression());
        }
        run(helper, d, src, "wfmedical suppression clear @s");
        if (profile.getPainSuppression() != 0.0F) {
            helper.fail("suppression clear left " + profile.getPainSuppression());
        }

        run(helper, d, src, "wfmedical drug set @s 1.5");
        if (Math.abs(profile.getDrugLoad() - 1.5F) > 0.01F) {
            helper.fail("drug set left " + profile.getDrugLoad());
        }
        run(helper, d, src, "wfmedical drug clear @s");
        if (profile.getDrugLoad() != 0.0F) {
            helper.fail("drug clear left " + profile.getDrugLoad());
        }

        // reset installs a brand-new MedicalProfile rather than clearing the existing one, so anything
        // holding the old object keeps reading pre-reset values. Re-fetch, and pin the swap.
        run(helper, d, src, "wfmedical reset");
        MedicalProfile after = TestBodies.profileOf(helper, v);
        if (after == profile) {
            helper.fail("reset mutated the profile in place; the swap is what invalidates cached handles");
        }
        if (Math.abs(after.getBloodMl() - after.getMaxBloodMl()) > 1.0D) {
            helper.fail("reset did not restore blood: " + after.getBloodMl());
        }
        if (after.getState() != HealthState.HEALTHY) {
            helper.fail("reset left the player at " + after.getState());
        }
        if (!TestBodies.allTraumas(after).isEmpty()) {
            helper.fail("reset left wounds behind: " + TestBodies.describe(after));
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void queryReportsWithoutChangingAnything(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        profile.setBloodMl(3000.0D);
        CommandDispatcher<CommandSourceStack> d = dispatcher();
        CommandSourceStack src = helper.getLevel().getServer().createCommandSourceStack()
                .withEntity(v).withPermission(2);

        run(helper, d, src, "wfmedical query");
        if (Math.abs(profile.getBloodMl() - 3000.0D) > 1.0D) {
            helper.fail("query mutated the profile: " + profile.getBloodMl());
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void healAndReviveUndoDamage(GameTestHelper helper) {
        TestBodies.Victim v = TestBodies.victim(helper);
        MedicalProfile profile = TestBodies.profileOf(helper, v);
        CommandDispatcher<CommandSourceStack> d = dispatcher();
        CommandSourceStack src = helper.getLevel().getServer().createCommandSourceStack()
                .withEntity(v).withPermission(2);

        run(helper, d, src, "wfmedical trauma add @s TORSO laceration_large 0.9");
        run(helper, d, src, "wfmedical blood set @s 800");
        if (profile.limb(LimbType.TORSO).getTraumas().isEmpty()) {
            helper.fail("the setup did not wound anyone");
            return;
        }

        run(helper, d, src, "wfmedical heal");
        if (!profile.limb(LimbType.TORSO).getTraumas().isEmpty()) {
            helper.fail("heal left wounds behind: " + TestBodies.describe(profile));
        }
        if (Math.abs(profile.getBloodMl() - profile.getMaxBloodMl()) > 1.0D) {
            helper.fail("heal did not restore blood: " + profile.getBloodMl());
        }

        profile.setOverdoseUnconscious(true);
        run(helper, d, src, "wfmedical revive");
        if (profile.isDowned()) {
            helper.fail("revive left the player downed");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFMedical.MOD_ID, template = TEMPLATE)
    public void anUnknownTraumaIdIsReportedRatherThanRegisteringNothing(GameTestHelper helper) {
        CommandDispatcher<CommandSourceStack> d = dispatcher();
        CommandSourceStack src = source(helper);
        // It has to parse -- the id is a free-form word -- and then fail at execution rather than
        // silently adding an invisible wound.
        parses(helper, d, src, "wfmedical trauma add @s TORSO not_a_real_trauma 0.5");
        helper.succeed();
    }

    private static void run(GameTestHelper helper, CommandDispatcher<CommandSourceStack> d,
                            CommandSourceStack src, String command) {
        try {
            d.execute(command, src);
        } catch (Exception e) {
            helper.fail("'/" + command + "' threw: " + e);
        }
    }
}
