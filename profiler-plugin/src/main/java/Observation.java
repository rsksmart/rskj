import java.time.Duration;

public class Observation {

    private final String category;
    private final Duration duration;

    Observation(String category, Duration duration) {

        this.category = category;
        this.duration = duration;
    }

    public String getCategory() {
        return category;
    }

    public Duration getDuration() {
        return duration;
    }
}
