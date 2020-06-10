package com.zucchetti.dc.zprobe.core;

import java.io.IOException;
import java.io.Writer;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.locks.AbstractOwnableSynchronizer;


/**
 * Classe di utilità che esegue il dump di tutti i threads attivi della JVM.
 * 
 * <p>Esempio di configurazione minima:</p>
 * <pre><tt>StringWriter sw = new StringWriter();
 *	ThreadsDumper.dump(sw, ThreadsDumperConf.builder().maxFramesSize().build());
 * 	System.out.println(sw.toString());
 * </tt></pre>
 * 
 * Il dump dei threads utilizza la classe {@link ThreadMXBean} e nello specifico il metodo {@link ThreadMXBean#dumpAllThreads(boolean, boolean)}
 * 
 * <p>
 * Il metodo per il dump di tutti i threads ha due parametri:
 * </p>
 * 
 * <ul>
 * <li>lockedMonitors if <tt>true</tt>, dump all locked monitors.</li>
 * <li>lockedSynchronizers if <tt>true</tt>, dump all locked ownable synchronizers.</li>
 * </ul>
 * 
 * <p>
 * I <b>lockedMonitors</b> sono i locks basati sul blocco synchronized.
 * I <b>lockedSynchronizers</b> sono i locks espliciti basati sulla classe {@link AbstractOwnableSynchronizer}
 * </p>
 * 
 <br>
 <b>CODICE SORGENTE lockedMonitors</b>
 <pre><tt>
 private void testSynchronizedBlock() 
 {
	new Thread(new Runnable() 
	{
		public void run()
		{
			synchronized (monitor) 
			{
				for(;;) {}
			}
		}
	}, "test-synchronized-block").start();
 }
 </tt></pre>
<b>THREAD DUMP lockedMonitors</b>
<pre><tt>
"test-synchronized-block" Id=10 RUNNABLE
	at com.zucchetti.fui.core.utils.test.threads.ThreadsDumperTest$5.run(ThreadsDumperTest.java:156)
	-  locked java.lang.Object@47a38913
	at java.lang.Thread.run(Thread.java:744)
 </tt></pre>
 <br>
 <b>CODICE SORGENTE lockedSynchronizers</b>
 <pre><tt>
 private void testExplicitLockOwner()
 {
	new Thread(new Runnable() 
	{
		public void run()
		{
			lock.lock(); // Lock lock = new ReentrantLock();
			try
			{
					for(;;) {}
			}
			finally
			{
				lock.unlock();
			}
		}
	}, "test-explicit-lock-owner").start();
   }
 </tt></pre>
<b>THREAD DUMP lockedSynchronizers</b>
<pre><tt>
"test-explicit-lock-owner" Id=11 RUNNABLE
	at com.zucchetti.fui.core.utils.test.threads.ThreadsDumperTest$4.run(ThreadsDumperTest.java:137)
	at java.lang.Thread.run(Thread.java:744)

	Number of locked synchronizers = 1
	- java.util.concurrent.locks.ReentrantLock$NonfairSync@38074e6d
 </tt></pre>
 <pre><tt>
"test-explicit-lock-waiting" Id=12 WAITING on java.util.concurrent.locks.ReentrantLock$NonfairSync@4d50b06b owned by "test-explicit-lock-owner" Id=11
	at sun.misc.Unsafe.park(Native Method)
	-  waiting on java.util.concurrent.locks.ReentrantLock$NonfairSync@4d50b06b
	at java.util.concurrent.locks.LockSupport.park(LockSupport.java:186)
	at java.util.concurrent.locks.AbstractQueuedSynchronizer.parkAndCheckInterrupt(AbstractQueuedSynchronizer.java:834)
	at java.util.concurrent.locks.AbstractQueuedSynchronizer.acquireQueued(AbstractQueuedSynchronizer.java:867)
	at java.util.concurrent.locks.AbstractQueuedSynchronizer.acquire(AbstractQueuedSynchronizer.java:1197)
	at java.util.concurrent.locks.ReentrantLock$NonfairSync.lock(ReentrantLock.java:214)
	at java.util.concurrent.locks.ReentrantLock.lock(ReentrantLock.java:290)
	at com.zucchetti.fui.core.utils.test.threads.ThreadsDumperTest$3.run(ThreadsDumperTest.java:114)
	at java.lang.Thread.run(Thread.java:744)
 </tt></pre>
 * 
 * Per approfondimenti ed altri strumenti per eseguire un threads dump vedi: https://dzone.com/articles/how-to-take-thread-dumps-7-options
 * @see ThreadMXBean
 * @since 1.6
 * 
 * @author GROMAS
 */
public class ThreadsDumper 
{
	/**
	 * Filtro applicabile al dump dei threads.
	 * @author GROMAS
	 */
	public interface IThreadInfoFilter
	{
		/**
		 * @param threadInfo {@link ThreadInfo}
		 * @return true se il thread rappresentato dall'oggetto in input deve essere presente nel dump, false altrimenti.
		 */
		boolean accept(ThreadInfo threadInfo);
	}

	public static IThreadInfoFilter NO_FILTER = new IThreadInfoFilter() 
	{
		@Override
		public boolean accept(ThreadInfo threadInfo) 
		{
			return true;
		}
	};

	/**
	 * Configurazione per l'esecuzione del dump dei Threads
	 * @author GROMAS
	 */
	public static class ThreadsDumperConf
	{
		private final boolean lockedMonitors;
		private final boolean lockedSynchronizers;
		private final int maxPrintableFramesNumber;
		private final IThreadInfoFilter filter;

		private ThreadsDumperConf(Builder builder) 
		{
			this.lockedMonitors = builder.lockedMonitors;
			this.lockedSynchronizers = builder.lockedSynchronizers;
			this.maxPrintableFramesNumber = builder.maxPrintableFramesNumber;
			this.filter = builder.filter;
		}

		/**
		 * @return true se è abilitata la tracciatura dei monitor locked.
		 */
		public boolean isLockedMonitorsEnabled() 
		{
			return lockedMonitors;
		}

		/**
		 * @return true se è abiltiata la tracciatura dei locks espliciti basati su {@link AbstractOwnableSynchronizer}
		 */
		public boolean isLockedSynchronizersEnabled() 
		{
			return lockedSynchronizers;
		}

		/**
		 * @return Numero massimo di frames dello stacktrace stampabili.
		 */
		public int getMaxPrintableFramesNumber()
		{
			return maxPrintableFramesNumber;
		}

		/**
		 * @return Implementazione dell'interfaccai {@link IThreadInfoFilter} utile ad applicare un filtro su specifici Threads.
		 */
		public IThreadInfoFilter threadsFilter()
		{
			return filter;
		}

		/**
		 * Nuova istanza di builder per la costruzione del prodotto {@link ThreadsDumperConf}
		 * Di default sono disabilitate le tracciature per i locks ed i filtri sui threads.
		 * 
		 * @return Nuovo builder per la costruzione di una nuova configurazione per il Threads Dumper {@link ThreadsDumperConf}
		 */
		public static BuilderFramesSizeStep builder() 
		{
			return new Builder();
		}

		/**
		 * Builder per la costruzione della configurazione del dumper {@link ThreadsDumperConf}
		 * @author GROMAS 
		 */
		private static final class Builder implements BuilderFramesSizeStep, BuilderStep 
		{
			private static final int MAX_FRAMES_SIZE = 120;
			private static final int MEDIUM_FRAMES_SIZE = 60;
			private static final int DEAFULT_FRAMES_SIZE = 8;

			private boolean lockedMonitors = false;
			private boolean lockedSynchronizers = false;
			private int maxPrintableFramesNumber = DEAFULT_FRAMES_SIZE;
			private IThreadInfoFilter filter = NO_FILTER;

			private Builder() {}

			/*
			 * (non-Javadoc)
			 * @see com.zucchetti.fui.core.utils.threads.ThreadsDumper.BuilderStep#lockedMonitors(boolean)
			 */
			@Override
			public BuilderStep lockedMonitors(boolean lockedMonitors) 
			{
				this.lockedMonitors = lockedMonitors;
				return this;
			}

			/*
			 * (non-Javadoc)
			 * @see com.zucchetti.fui.core.utils.threads.ThreadsDumper.BuilderStep#lockedSynchronizers(boolean)
			 */
			@Override
			public BuilderStep lockedSynchronizers(boolean lockedSynchronizers) 
			{
				this.lockedSynchronizers = lockedSynchronizers;
				return this;
			}

			/*
			 * (non-Javadoc)
			 * @see com.zucchetti.fui.core.utils.threads.ThreadsDumper.BuilderStep#threadsFilter(com.zucchetti.fui.core.utils.threads.ThreadsDumper.IThreadInfoFilter)
			 */
			@Override
			public BuilderStep threadsFilter(IThreadInfoFilter threadInfoFilter)
			{
				this.filter = threadInfoFilter;
				return this;
			}

			/*
			 * (non-Javadoc)
			 * @see com.zucchetti.fui.core.utils.threads.ThreadsDumper.BuilderFramesSizeStep#minFramesSize()
			 */
			@Override
			public BuilderStep minFramesSize() 
			{
				this.maxPrintableFramesNumber = DEAFULT_FRAMES_SIZE;
				return this;
			}

			/*
			 * (non-Javadoc)
			 * @see com.zucchetti.fui.core.utils.threads.ThreadsDumper.BuilderFramesSizeStep#mediumFramesSize()
			 */
			@Override
			public BuilderStep mediumFramesSize() 
			{
				this.maxPrintableFramesNumber = MEDIUM_FRAMES_SIZE;
				return this;
			}

			/*
			 * (non-Javadoc)
			 * @see com.zucchetti.fui.core.utils.threads.ThreadsDumper.BuilderFramesSizeStep#maxFramesSize()
			 */
			@Override
			public BuilderStep maxFramesSize() 
			{
				this.maxPrintableFramesNumber = MAX_FRAMES_SIZE;
				return this;
			}

			/*
			 * (non-Javadoc)
			 * @see com.zucchetti.fui.core.utils.threads.ThreadsDumper.BuilderStep#build()
			 */
			@Override
			public ThreadsDumperConf build() 
			{
				return new ThreadsDumperConf(this);
			}
		}
	}

	/**
	 * Step iniziale per la costruzione della configurazione {@link ThreadsDumperConf} del threads dumper. 
	 * In questa fase deve essere scelto il numero massimo di frames stampabili nel dump.
	 * 
	 * @author GROMAS
	 */
	public interface BuilderFramesSizeStep
	{
		/**
		 * Imposta il numero minimo (8) di Frames dello stacktrace
		 * @return Step successivo del processo di build {@link ThreadsDumperConf#builder()}
		 */
		BuilderStep minFramesSize();

		/**
		 * Imposta il numero medio (60) di Frames dello stacktrace
		 * @return Step successivo del processo di build {@link ThreadsDumperConf#builder()}
		 */
		BuilderStep mediumFramesSize();

		/**
		 * Imposta il numero massimo (120) di Frames dello stacktrace
		 * @return Step successivo del processo di build {@link ThreadsDumperConf#builder()}
		 */
		BuilderStep maxFramesSize(); 
	}

	/**
	 * Step finale per la costruzione della configurazione {@link ThreadsDumperConf} del threads dumper. 
	 * @author GROMAS
	 */
	public interface BuilderStep
	{
		/**
		 * @return Nuova configurazione per l'esecuzione del dump dei Threads
		 */
		ThreadsDumperConf build();

		/**
		 * @param lockedMonitors flag per impostare la tracciatura dei monitor locked.
		 * @return Step successivo del processo di build {@link ThreadsDumperConf#builder()}
		 */
		BuilderStep lockedMonitors(boolean lockedMonitors);

		/**
		 * @param lockedSynchronizers flag per impostare la tracciatura dei lock espliciti che si basano sulla classe {@link AbstractOwnableSynchronizer}
		 * @return Step successivo del processo di build {@link ThreadsDumperConf#builder()}
		 */
		BuilderStep lockedSynchronizers(boolean lockedSynchronizers);

		/**
		 * Permette di specificare una regola di filtro da applicare ai Threads che andranno a comporre il dump.
		 * @param threadInfoFilter Filtro da applicare ai Threads.
		 * @return Step successivo del processo di build {@link ThreadsDumperConf#builder()}
		 */
		BuilderStep threadsFilter(IThreadInfoFilter threadInfoFilter);
	}

	/**
	 * Esegue il dump di tutti i Threads della JVM.
	 * 
	 * @see ThreadMXBean#dumpAllThreads(boolean, boolean)
	 * 
	 * @since 1.6
	 * 
	 * @param writer Writer sul quale si vuole la stampa di tutti i dump.
	 * @param conf Configurazione per l'esecuzione del Dump dei Threads.
	 */
	public static void dump(Writer writer, ThreadsDumperConf conf)
	{
		try 
		{
			writeHeader(writer, conf);
			final ThreadInfo[] threadsInfo = ManagementFactory.getThreadMXBean().dumpAllThreads(conf.lockedMonitors, conf.lockedSynchronizers);
			Arrays.sort(threadsInfo, new Comparator<ThreadInfo>() 
			{
				@Override
				public int compare(ThreadInfo o1, ThreadInfo o2) 
				{
					// Ordinamento dal Thread con lo stacktrace composto da più frames prima.
					return Integer.compare(o1.getStackTrace().length, o2.getStackTrace().length) * -1;
				}
			});
			for(ThreadInfo ti : threadsInfo)
			{
				if(conf.filter.accept(ti))
				{
					writer.write(toString(ti, conf.maxPrintableFramesNumber));
				}
			}
		} 
		catch (IOException e) 
		{
			throw new RuntimeException(e);
		}
	}

	private static void writeHeader(Writer writer, ThreadsDumperConf conf) throws IOException 
	{
		String timeAsString = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss,SS").format(new Date());

		writer.write("JVM Threads dump on host [" + hostAddressAsString() + "] at [" + timeAsString + "]");
		writer.write("\n");
		writer.write("\n");
		writer.write("\t- lockedMonitors:=" + conf.lockedMonitors);
		writer.write("\n");
		writer.write("\t- lockedSynchronizers:=" + conf.lockedSynchronizers);
		writer.write("\n");
		writer.write("\t- maxPrintableFramesNumber:=" + conf.maxPrintableFramesNumber);
		writer.write("\n");
		writer.write("\t- filtered:=" + (conf.filter == NO_FILTER ? false : true));
		writer.write("\n");
		writer.write("\n");
	}

	private static String hostAddressAsString() 
	{
		try 
		{
			return InetAddress.getLocalHost().getHostAddress();
		} 
		catch (UnknownHostException e) 
		{
			return "unknowHost";
		}
	}

	/**
	 * Codice copiato dal metodo {@link ThreadInfo#toString()} e modificato, 
	 * per aumentare il numero massimo di Frames "Stampabili" di uno stacktrace.
	 * 
	 * @see ThreadInfo#toString() - Versione JAVA 7u51
	 * 
	 * @param ti {@link ThreadInfo}
	 * @param maxFrames Numero massimo di frame stampabili (originale è 8)
	 * @return StackTrace in formato stringa.
	 */
	private static String toString(ThreadInfo ti, int maxFrames) {
		StringBuilder sb = new StringBuilder("\"" + ti.getThreadName() + "\"" +
				" Id=" + ti.getThreadId() + " " +
				ti.getThreadState());
		if (ti.getLockName() != null) {
			sb.append(" on " + ti.getLockName());
		}
		if (ti.getLockOwnerName() != null) {
			sb.append(" owned by \"" + ti.getLockOwnerName() +
					"\" Id=" + ti.getLockOwnerId());
		}
		if (ti.isSuspended()) {
			sb.append(" (suspended)");
		}
		if (ti.isInNative()) {
			sb.append(" (in native)");
		}
		sb.append('\n');
		int i = 0;
		for (; i < ti.getStackTrace().length && i < maxFrames; i++) {
			StackTraceElement ste = ti.getStackTrace()[i];
			sb.append("\tat " + ste.toString());
			sb.append('\n');
			if (i == 0 && ti.getLockInfo() != null) {
				Thread.State ts = ti.getThreadState();
				switch (ts) {
				case BLOCKED:
					sb.append("\t-  blocked on " + ti.getLockInfo());
					sb.append('\n');
					break;
				case WAITING:
					sb.append("\t-  waiting on " + ti.getLockInfo());
					sb.append('\n');
					break;
				case TIMED_WAITING:
					sb.append("\t-  waiting on " + ti.getLockInfo());
					sb.append('\n');
					break;
				default:
				}
			}

			for (MonitorInfo mi : ti.getLockedMonitors()) {
				if (mi.getLockedStackDepth() == i) {
					sb.append("\t-  locked " + mi);
					sb.append('\n');
				}
			}
		}
		if (i < ti.getStackTrace().length) {
			sb.append("\t...");
			sb.append('\n');
		}

		LockInfo[] locks = ti.getLockedSynchronizers();
		if (locks.length > 0) {
			sb.append("\n\tNumber of locked synchronizers = " + locks.length);
			sb.append('\n');
			for (LockInfo li : locks) {
				sb.append("\t- " + li);
				sb.append('\n');
			}
		}
		sb.append('\n');
		return sb.toString();
	}
}
