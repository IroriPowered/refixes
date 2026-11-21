package cc.irori.refixes.command;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.PatchCounters;
import cc.irori.refixes.util.Early;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import java.awt.Color;

public class PatchCountersCommand extends CommandBase {

    public PatchCountersCommand() {
        super("patchcounters", "refixes.commands.patchcounters.desc");
        this.requirePermission(HytalePermissions.fromCommand("refixes.patchcounters"));
    }

    @Override
    protected void executeSync(CommandContext context) {
        if (!Early.isEnabled() || !EarlyOptions.hasMixinSelection()) {
            context.sender()
                    .sendMessage(
                            Message.raw("The early patches of Refixes are not active, so there is nothing to count")
                                    .color(Color.RED));
            return;
        }
        if (!PatchCounters.ENABLED) {
            context.sender()
                    .sendMessage(Message.raw("Patch counters are " + PatchCounters.OFF_MESSAGE)
                            .color(Color.YELLOW));
            return;
        }
        context.sender()
                .sendMessage(Message.raw("Patch counters since server start:").color(Color.CYAN));
        for (PatchCounters.Snapshot counter : PatchCounters.snapshot()) {
            context.sender()
                    .sendMessage(Message.raw(
                            "  - " + counter.key() + (counter.enabled() ? " (on): " : " (off): ") + counter.counts()));
        }
    }
}
