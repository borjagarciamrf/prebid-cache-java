package org.prebid.cache.metrics;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import org.prebid.cache.handlers.GetCacheHandler;
import org.prebid.cache.handlers.PostCacheHandler;
import org.prebid.cache.handlers.ServiceType;
import org.prebid.cache.routers.ApiRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricRegistry.name;

@Slf4j
@Component
public class GraphiteMetricsRecorder extends MetricsRecorder
{
    final private static MetricRegistry registry = new MetricRegistry();
    final private GraphiteConfig config;

    // GET endpoint metrics
    private static final Timer requestGetDuration = registry.timer(name(GetCacheHandler.class, MeasurementTag.REQUEST_DURATION.getTag()));

    // POST endpoint metrics
    private static final Timer requestPostDuration = registry.timer(name(PostCacheHandler.class, MeasurementTag.REQUEST_DURATION.getTag()));

    // Other 404
    private static final Meter invalidRequestMeter = registry.meter(name(ApiRouter.class, MeasurementTag.INVALID_REQUEST_RATE.getTag()));

    @Autowired
    public GraphiteMetricsRecorder(final GraphiteConfig config) {
        this.config = config;
        startReport();
    }

    private void startReport() {
        if (!config.isEnabled())
            return;

        log.info("Starting {} host - [{}:{}].", GraphiteMetricsRecorder.class.getCanonicalName(), config.getHost(), config.getPort());
        final Graphite graphite = new Graphite(new InetSocketAddress(config.getHost(), config.getPort()));
        final GraphiteReporter reporter = GraphiteReporter.forRegistry(registry)
                .prefixedWith(config.getPrefix())
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build(graphite);
        reporter.start(1, TimeUnit.MINUTES);
    }

    public Meter getInvalidRequestMeter() {
        return invalidRequestMeter;
    }

    private Meter meterForClass(final Class cls, final MeasurementTag measurementTag) {
        return registry.meter(name(cls, measurementTag.getTag()));
    }

    public void markMeterForClass(final Class cls, final MeasurementTag measurementTag) {
        meterForClass(cls, measurementTag).mark();
    }

    public Optional<Timer.Context> createRequestContextTimerOptionalForServiceType(final ServiceType serviceType) {
        final Timer timer = getRequestTimerForServiceType(serviceType);
        if (timer != null)
            return Optional.of(timer.time());
        return Optional.empty();
    }

    private Timer getRequestTimerForServiceType(final ServiceType serviceType) {
        if (serviceType.equals(ServiceType.FETCH)) {
            return requestGetDuration;
        } else if (serviceType.equals(ServiceType.SAVE)) {
            return requestPostDuration;
        }
        return null;
    }
}
