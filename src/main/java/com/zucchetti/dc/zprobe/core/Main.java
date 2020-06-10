package com.zucchetti.dc.zprobe.core;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;

import javax.management.MBeanException;
import javax.management.ReflectionException;

import com.sun.management.DiagnosticCommandMBean;

import sun.management.ManagementFactoryHelper;

@SuppressWarnings("restriction")
public class Main
{

	/**
	 * https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/tooldescr006.html
	 * https://docs.oracle.com/javase/8/docs/jre/api/management/extension/com/sun/management/DiagnosticCommandMBean.html
	 * @throws ReflectionException 
	 * @throws MBeanException 
	 */
	public static void main(String[] args) throws MBeanException, ReflectionException
	{
		System.out.println("ZProbe-Core MAIN");
		
		 // Do thread dumps in two different ways (to exercise different code paths)
	    // while the old class is still on the stack
	    ThreadInfo[] tis = ManagementFactory.getThreadMXBean().dumpAllThreads(false, false);
	    for (ThreadInfo ti : tis) {
	        System.out.println(ti);
	    }
	    String[] threadPrintArgs = {};
	    Object[] dcmdArgs = { threadPrintArgs };
	    String[] signature = { String[].class.getName() };
	    DiagnosticCommandMBean dcmd = ManagementFactoryHelper.getDiagnosticCommandMBean();
	    System.out.println(dcmd.invoke("threadPrint", dcmdArgs, signature));
	}
}
