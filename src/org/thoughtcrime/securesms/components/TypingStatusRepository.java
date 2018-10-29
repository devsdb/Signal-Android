package org.thoughtcrime.securesms.components;

import android.annotation.SuppressLint;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.support.annotation.NonNull;

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@SuppressLint("UseSparseArrays")
public class TypingStatusRepository {

  private static final long RECIPIENT_TYPING_TIMEOUT = TimeUnit.SECONDS.toMillis(15);

  private final Map<Long, Set<Typist>>                      typistMap;
  private final Map<Long, MutableLiveData<List<Recipient>>> notifiers;
  private final Set<Long>                                   activeThreads;
  private final MutableLiveData<Set<Long>>                  threadsNotifier;
  private final Map<Typist, Runnable>                       timers;

  public TypingStatusRepository() {
    this.typistMap       = new HashMap<>();
    this.notifiers       = new HashMap<>();
    this.activeThreads   = new HashSet<>();
    this.threadsNotifier = new MutableLiveData<>();
    this.timers          = new HashMap<>();
  }

  public synchronized void onTypingStarted(long threadId, Recipient author, int device) {
    Set<Typist> typists = Util.getOrDefault(typistMap, threadId, new LinkedHashSet<>());
    Typist      typist  = new Typist(author, device, threadId);

    if (!typists.contains(typist)) {
      typists.add(typist);
      typistMap.put(threadId, typists);
      notifyThread(threadId, typists);
    }

    Runnable timer = timers.get(typist);
    if (timer != null) {
      Util.cancelRunnableOnMain(timer);
    }

    timer = () -> onTypingStopped(threadId, author, device);
    Util.runOnMainDelayed(timer, RECIPIENT_TYPING_TIMEOUT);
    timers.put(typist, timer);
  }

  public synchronized void onTypingStopped(long threadId, Recipient author, int device) {
    Set<Typist> typists = Util.getOrDefault(typistMap, threadId, new LinkedHashSet<>());
    Typist      typist  = new Typist(author, device, threadId);

    if (typists.contains(typist)) {
      typists.remove(typist);
      notifyThread(threadId, typists);
    }

    Runnable timer = timers.get(typist);
    if (timer != null) {
      Util.cancelRunnableOnMain(timer);
      timers.remove(typist);
    }
  }

  public synchronized LiveData<List<Recipient>> getTypists(long threadId) {
    MutableLiveData<List<Recipient>> notifier = Util.getOrDefault(notifiers, threadId, new MutableLiveData<>());
    notifiers.put(threadId, notifier);
    return notifier;
  }

  public synchronized LiveData<Set<Long>> getTypingThreads() {
    return threadsNotifier;
  }

  public synchronized void clear() {
    List<Recipient> empty = Collections.emptyList();
    for (MutableLiveData<List<Recipient>> notifier : notifiers.values()) {
      notifier.postValue(empty);
    }
    notifiers.clear();
    typistMap.clear();
    timers.clear();

    activeThreads.clear();
    threadsNotifier.postValue(Collections.emptySet());
  }

  private void notifyThread(long threadId, @NonNull Set<Typist> typists) {
    MutableLiveData<List<Recipient>> notifier = Util.getOrDefault(notifiers, threadId, new MutableLiveData<>());
    notifiers.put(threadId, notifier);

    Set<Recipient> uniqueTypists = new LinkedHashSet<>();
    for (Typist typist : typists) {
      uniqueTypists.add(typist.getAuthor());
    }

    notifier.postValue(new ArrayList<>(uniqueTypists));

    if (!uniqueTypists.isEmpty()) {
      activeThreads.add(threadId);
    } else {
      activeThreads.remove(threadId);
    }

    threadsNotifier.postValue(activeThreads);
  }

  private static class Typist {
    private final Recipient author;
    private final int       device;
    private final long      threadId;

    private Typist(@NonNull Recipient author, int device, long threadId) {
      this.author   = author;
      this.device   = device;
      this.threadId = threadId;
    }

    public Recipient getAuthor() {
      return author;
    }

    public int getDevice() {
      return device;
    }

    public long getThreadId() {
      return threadId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Typist typist = (Typist) o;

      if (device != typist.device) return false;
      if (threadId != typist.threadId) return false;
      return author.equals(typist.author);
    }

    @Override
    public int hashCode() {
      int result = author.hashCode();
      result = 31 * result + device;
      result = 31 * result + (int) (threadId ^ (threadId >>> 32));
      return result;
    }
  }
}
