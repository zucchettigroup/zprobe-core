package com.zucchetti.dc.zprobe.core;

import javax.management.MBeanException;
import javax.management.ReflectionException;

import com.sun.management.DiagnosticCommandMBean;

import sun.management.ManagementFactoryHelper;

@SuppressWarnings("restriction")
public class ThreadsDumperDiagnostic
{
	public static String threadPrint() throws MBeanException, ReflectionException
	{
	    String[] threadPrintArgs = {};
	    Object[] dcmdArgs = { threadPrintArgs };
	    String[] signature = { String[].class.getName() };
	    DiagnosticCommandMBean dcmd = ManagementFactoryHelper.getDiagnosticCommandMBean();
	    return (String) dcmd.invoke("threadPrint", dcmdArgs, signature);
	}
}
