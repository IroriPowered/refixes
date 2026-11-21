package cc.irori.refixes.jfr;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Period;
import jdk.jfr.StackTrace;

@Name("refixes.Metric")
@Label("Refixes Metric")
@Category({"Mods", "Refixes"})
@Description("A Refixes counter or gauge, emitted every 5 seconds for every registered metric")
@Period("5 s")
@StackTrace(false)
public final class MetricEvent extends Event {

    @Label("Name")
    @Description("Metric name, for example AiTickThrottler froze")
    public String name;

    @Label("Kind")
    @Description("counter or gauge")
    public String kind;

    @Label("Value")
    @Description("For a counter the change since its previous event, for a gauge the current value")
    public double value;
}
