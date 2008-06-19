/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugin.jdee.support;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/** Class which wrap process and give some new function to it
 * @author <a href="mailto:bendal@apnet.cz">Lukas Benda</a>
 * @version 1.2.2
 */
public class AsynchronousProcess implements Runnable {

  /** Describe errorStream here. */
  private PrintStream errorStream;
  /** Describe inputStream here. */
  private PrintStream inputStream;
  /** Describe outputStream here. */
  private OutputStream outputStream;
  /** Wrapped process */
  private Process process;
  /** Process wrapper */
  private PriTh processWrapper;
  /** finished */
  private boolean finish = false;

  /** Creates a new <code>AsynchronousProcess</code> instance.
   * @param process process which will be run asynchronous
   */
  public AsynchronousProcess(final Process process) {
    super();
    this.process = process;
    processWrapper = new PriTh(process, this);
  }

  /** Methode which return process
   * @return wrapped process
   */
  public final Process getProcess() {
    return process;
  }

  /** Get the <code>ErrorStream</code> value.
   * @return a value
   */
  public final PrintStream getErrorStream() {
    return errorStream;
  }

  /** Set the <code>ErrorStream</code> value.
   * @param newErrorStream The new ErrorStream value.
   */
  public final void setErrorStream(final PrintStream newErrorStream) {
    this.errorStream = newErrorStream;
  }

  /** Get the <code>PrintStream</code> value.
   * @return a value
   */
  public final PrintStream getInputStream() {
    return inputStream;
  }

  /** Set the <code>InputStream</code> value.
   * @param newInputStream The new InputStream value.
   */
  public final void setInputStream(final PrintStream newInputStream) {
    this.inputStream = newInputStream;
  }

  /** Get the <code>OutputStream</code> value.
   * @return a value
   */
  public final OutputStream getOutputStream() {
    return outputStream;
  }

  /** Set the <code>OutputStream</code> value.
   * @param newOutputStream The new OutputStream value.
   */
  public final void setOutputStream(final OutputStream newOutputStream) {
    this.outputStream = newOutputStream;
  }

  /** Methode which run asynchronous chack of project */
  public final void run() {
    int breaked = 0;

    while (!isFinish()) {
      try {
        wait(100);
      } catch (InterruptedException e) {
        // ignore
      } catch (Exception e) {
        // ignore
      }
      try {
        System.out.println("Exit value: " + process.exitValue());
        finish = true;
      } catch (Exception e) {
        // ignore
      }

      if (getInputStream() != null) {
        try {
          byte[] byt = new byte[process.getInputStream().available()];
          int readed = process.getInputStream().read(byt);
          if (readed > 0) {
            getInputStream().write(byt, 0, readed);
            getInputStream().flush();
          }
        } catch (IOException e) {
          // ignore
        }
      }

      if (getErrorStream() != null) {
        try {
          byte[] byt = new byte[process.getErrorStream().available()];
          int readed = process.getErrorStream().read(byt);
          if (readed > 0) {
            getErrorStream().write(byt, 0, readed);
            getErrorStream().flush();
          }
        } catch (IOException e) {
          // ignore
        }
      }
    }
  }

  /** Get the <code>Finish</code> value.
   * @return a value
   */
  public final boolean isFinish() {
    return finish;
  }

  private class PriTh implements Runnable {

    Thread thread;

    /** Describe finish here. */
    private boolean finish = false;
    /** Wrapped process */
    private Process process;
    /** Caller object */
    private Object caller;

    public PriTh(final Process process, final Object caller)  {
      this.process = process;
      this.caller = caller;
      thread = new Thread(this);
    }

    public void run() {
      finish = false;
      try {
        boolean fi = true;
        while (!fi) {
          wait(100);
          try {
            System.out.println("Exit status: " + process.exitValue());
          } catch (IllegalThreadStateException e) {
            System.out.println("Not end yet");
            fi = false;
          }
        }
      } catch (InterruptedException e) {
        // ignore
      }
      finish = true;
      caller.notify();
    }

    /** Get the <code>Finish</code> value.
     * @return a value
     */
    public final boolean isFinish() {
      return finish;
    }
  }
}
