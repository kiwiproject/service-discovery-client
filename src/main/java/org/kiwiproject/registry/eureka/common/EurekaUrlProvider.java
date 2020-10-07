package org.kiwiproject.registry.eureka.common;

import static java.util.Objects.isNull;
import static org.kiwiproject.base.KiwiStrings.splitOnCommas;
import static org.kiwiproject.net.KiwiUrls.stripTrailingSlashes;

import com.google.common.collect.Iterators;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class EurekaUrlProvider {

    private final Iterator<String> eurekaUrlCycler;
    private final Lock cyclerLock;
    private final AtomicReference<String> currentEurekaUrl;
    private final List<String> eurekaUrls;

    public EurekaUrlProvider(String commaSeparatedUrls) {
        this.eurekaUrls = stripTrailingSlashes(splitOnCommas(commaSeparatedUrls));
        this.eurekaUrlCycler = Iterators.cycle(eurekaUrls);
        this.cyclerLock = new ReentrantLock();
        this.currentEurekaUrl = new AtomicReference<>();
    }

    public int urlCount() {
        return eurekaUrls.size();
    }

    public String getCurrentEurekaUrl() {
        if (isNull(currentEurekaUrl.get())) {
            return getNextEurekaUrl();
        }

        return currentEurekaUrl.get();
    }

    public String getNextEurekaUrl() {
        try {
            cyclerLock.lock();
            var next = eurekaUrlCycler.next();
            currentEurekaUrl.set(next);
            return next;
        } finally {
            cyclerLock.unlock();
        }
    }
}
