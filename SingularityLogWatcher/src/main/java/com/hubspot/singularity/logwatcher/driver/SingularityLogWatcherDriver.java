package com.hubspot.singularity.logwatcher.driver;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.hubspot.mesos.JavaUtils;
import com.hubspot.singularity.logwatcher.LogForwarder;
import com.hubspot.singularity.logwatcher.SimpleStore;
import com.hubspot.singularity.logwatcher.SimpleStore.StoreException;
import com.hubspot.singularity.logwatcher.TailMetadata;
import com.hubspot.singularity.logwatcher.TailMetadataListener;
import com.hubspot.singularity.logwatcher.config.SingularityLogWatcherConfiguration;
import com.hubspot.singularity.logwatcher.tailer.SingularityLogWatcherTailer;

public class SingularityLogWatcherDriver implements TailMetadataListener {

  private final static Logger LOG = LoggerFactory.getLogger(SingularityLogWatcherDriver.class);

  private final SimpleStore store;
  private final LogForwarder logForwarder;
  private final SingularityLogWatcherConfiguration configuration;
  private final ExecutorService tailService;
  private final Map<TailMetadata, SingularityLogWatcherTailer> tailers;
  
  private volatile boolean shutdown;
  private final Lock shutdownLock;
  
  @Inject
  public SingularityLogWatcherDriver(SimpleStore store, SingularityLogWatcherConfiguration configuration, LogForwarder logForwarder) {
    this.store = store;
    this.logForwarder = logForwarder;
    this.configuration = configuration;
    this.tailers = Maps.newConcurrentMap();
    this.tailService = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("SingularityLogWatcherTailThread-%d").build());
    this.shutdown = false;
    this.shutdownLock = new ReentrantLock();
  }
  
  private void tail(final TailMetadata tail) {
    final SingularityLogWatcherTailer tailer = buildTailer(tail);

    tailService.submit(new Runnable() {
      
      @Override
      public void run() {
        try {
          tailer.watch();
          
          if (!shutdown) {
            LOG.info("Consuming tail: {}", tail);
          
            tailer.consumeStream();
            store.markConsumed(tail);
          }
        } catch (StoreException storeException) {
          LOG.error("While tailing: {}", tail, storeException);
          shutdown();
        } catch (Throwable t) {
          LOG.error("While tailing {}, will not retry until JVM is restarted or a new notification is posted", tail, t);
        } finally {
          tailer.close();

          tailers.remove(tail);
        }
      }
    });
    
    tailers.put(tail, tailer);
  }
  
  public void start() {
    final long start = System.currentTimeMillis();
    
    shutdownLock.lock();
    
    try {
      if (shutdown) {
        LOG.info("Not starting, was already shutdown");
        return;
      }
      
      for (TailMetadata tail : store.getTails()) {
        tail(tail);
      }
    } finally {
      shutdownLock.unlock();
    }
        
    LOG.info("Started {} tail(s) in {}", tailers.size(), JavaUtils.duration(start));
  
    store.start();
  }

  public void markShutdown() {
    shutdownLock.lock();
    this.shutdown = true;
    shutdownLock.unlock();
  }
  
  public void shutdown() {
    final long start = System.currentTimeMillis();
    
    LOG.info("Shutting down with {} tailer(s)", tailers.size());
    
    markShutdown();
    
    for (SingularityLogWatcherTailer tailer : tailers.values()) {
      tailer.stop();
    }
    
    tailService.shutdown();
    
    try {
      tailService.awaitTermination(1L, TimeUnit.DAYS);
    } catch (Throwable t) {
      LOG.error("While awaiting tail service", t);
    }
    
    try {
      store.close();
    } catch (Throwable t) {
      LOG.error("While closing store", t);
    }
    
    LOG.info("Shutdown after {}", JavaUtils.duration(start));
  }
  
  private SingularityLogWatcherTailer buildTailer(TailMetadata tail) {
    return new SingularityLogWatcherTailer(tail, configuration, store, logForwarder);
  }
  
  @Override
  public void tailChanged(TailMetadata tailMetadata) {
    shutdownLock.lock();
    
    try {
    
      if (shutdown) {
        LOG.info("Not handling notification {}, shutting down...", tailMetadata);
        return;
      }
      
      final SingularityLogWatcherTailer tailer = tailers.get(tailMetadata);
      
      if (tailer != null) {
        if (tailMetadata.isFinished()) {
          tailer.stop();
        } else {
          LOG.info("Ignoring notification about {} since we already had a tailer for it", tailMetadata);
        }
      } else {
        tail(tailMetadata);
      }
      
    } finally {
      shutdownLock.unlock();
    }
  }

}