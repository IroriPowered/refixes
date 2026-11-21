package cc.irori.refixes.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Period;
import jdk.jfr.StackTrace;

@Name("refixes.Setting")
@Label("Refixes Setting")
@Category({"Mods", "Refixes"})
@Description("Refixes version, patch state and every setting that differs from its default, at the start of each chunk")
@Period("beginChunk")
@StackTrace(false)
public final class SettingEvent extends Event {

    @Label("Name")
    public String name;

    @Label("Value")
    public String value;
}
