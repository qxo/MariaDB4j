/*
 * Copyright (c) 2012 Michael Vorburger
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * See also http://www.apache.org/licenses/LICENSE-2.0.html for an
 * explanation of the license and how it is applied.
 */
package ch.vorburger.exec;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import junit.framework.Assert;

import org.junit.Test;

import ch.vorburger.exec.Platform.Type;
import ch.vorburger.mariadb4j.MariaDB4jException;

/**
 * Tests ManagedProcess.
 * 
 * @author Michael Vorburger
 */
public class ManagedProcessTest {

	@Test
	public void testBasics() throws Exception {
		ManagedProcess p = new ManagedProcessBuilder("someExec").build();
		assertThat(p.isAlive(), is(false));
		try {
			p.destroy();
			Assert.fail("Should have thrown an IllegalStateException");
		} catch (ManagedProcessException e) {
			// as expected
		}
		try {
			p.exitValue();
			Assert.fail("Should have thrown an ManagedProcessException");
		} catch (ManagedProcessException e) {
			// as expected
		}
		try {
			p.start();	
			Assert.fail("Should have thrown an ManagedProcessException");
		} catch (ManagedProcessException e) {
			// as expected
		}
// TODO Tests... !		
//		try {
//			p.waitFor();
//			Assert.fail("Should have thrown an IllegalStateException");
//		} catch (IllegalStateException e) {
//			// as expected
//		}
//		try {
//			p.waitFor(1234);
//			Assert.fail("Should have thrown an IllegalStateException");
//		} catch (IllegalStateException e) {
//			// as expected
//		}
//		try {
//			p.waitFor("Never say never...");
//			Assert.fail("Should have thrown an IllegalStateException");
//		} catch (IllegalStateException e) {
//			// as expected
//		}
//		try {
//			p.waitForAndDestroy(1234);
//			Assert.fail("Should have thrown an IllegalStateException");
//		} catch (IllegalStateException e) {
//			// as expected
//		}
	}
	
	@Test
	public void testWaitForSeenMessageIfAlreadyTerminated() throws Exception {
		SomeSelfTerminatingExec exec = someSelfTerminatingExec();
		ManagedProcess p = exec.proc;
		p.start();
		// for this test, do NOT use any wait*() anything here, just give it a moment...
		Thread.sleep(1000);
		// by now this process should have terminated itself
		// but this should not cause this to hang, but must return silently:
		p.waitForConsoleMessage(exec.msgToWaitFor);
	}

	@Test(expected=ManagedProcessException.class)
	public void testWaitForWrongMessageIfAlreadyTerminated() throws Exception {
		ManagedProcess p = someSelfTerminatingExec().proc;
		p.start();
		// for this test, do NOT use any wait*() anything here, just give it a moment...
		Thread.sleep(1000);
		// by now this process should have terminated itself
		// but this should not cause this to hang, but must throw an IllegalStateException
		p.waitForConsoleMessage("some console message which will never appear");
	}

	@Test
	public void testSelfTerminatingExec() throws Exception {
		SomeSelfTerminatingExec exec = someSelfTerminatingExec();
		ManagedProcess p = exec.proc;

		assertThat(p.isAlive(), is(false));
		p.setConsoleBufferMaxLines(25);
		p.start();
		// can't assertThat(p.isAlive(), is(true)); - if p finishes too fast, this fails - unreliable test :(
		
		p.waitForConsoleMessage(exec.msgToWaitFor);
		
		p.waitForSuccessExit();
		p.exitValue(); // just making sure it works, don't check, as Win/NIX diff.
		assertThat(p.isAlive(), is(false));
		
		String recentConsoleOutput = p.getConsole();
		assertTrue(recentConsoleOutput.length() > 10);
		assertTrue(recentConsoleOutput.contains("\n"));
		System.out.println("Recent max. " + p.getConsoleBufferMaxLines() + " lines of console output:");
		System.out.println(recentConsoleOutput);
	}

	class SomeSelfTerminatingExec {
		ManagedProcess proc;
		String msgToWaitFor;
	}
	
	protected SomeSelfTerminatingExec someSelfTerminatingExec() throws UnknownPlatformException, MariaDB4jException, ManagedProcessException {
		SomeSelfTerminatingExec r = new SomeSelfTerminatingExec();
		switch (Platform.is()) {
		case Windows:
			r.proc = new ManagedProcessBuilder("cmd.exe").addArgument("/C").addArgument("dir").addArgument("/X").build();
			r.msgToWaitFor = "bytes free";
			break;

		case Mac:
		case Linux:
		case Solaris:
			r.proc = new ManagedProcessBuilder("true").addArgument("--version").build();
			r.msgToWaitFor = "true (GNU coreutils)";
			break;

		default:
			throw new MariaDB4jException("Unexpected Platform, improve the test dude...");
		}
		
		return r;
	}

	@Test
	public void testMustTerminateExec() throws Exception {
		ManagedProcessBuilder pb;
		if (Platform.is(Type.Windows)) {
			pb = new ManagedProcessBuilder("notepad.exe");
		} else {
			pb = new ManagedProcessBuilder("vi");
		}
		
		ManagedProcess p = pb.build();
		assertThat(p.isAlive(), is(false));
		p.start();
		assertThat(p.isAlive(), is(true));
		p.waitForAnyExitMaxMsOrDestroy(200);
		assertThat(p.isAlive(), is(false));
		// can not: p.exitValue();
	}

	@Test(expected=ManagedProcessException.class)
	public void waitForMustFailIfNeverStarted() throws Exception {
		ManagedProcess p = new ManagedProcessBuilder("someExec").build();
		p.waitForAnyExit();
	}
	
}
