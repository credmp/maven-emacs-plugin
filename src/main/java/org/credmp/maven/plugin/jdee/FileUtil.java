/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.maven.plugin.jdee;

import java.io.File;

/** Methode which make support for work with wile and directories
 * @author Lukas Benda
 * @version 1.2
 */
public class FileUtil {

  /** Methode which delete all children of directory
   * @param directory directory which will be clean
   */
  public static void cleanDirectory(final File directory) {
    if (directory.isDirectory()) {
      File[] children = directory.listFiles();
      if (children != null) {
        for (int i = 0; i < children.length; i++) {
          if (children[i].isDirectory()) { cleanDirectory(children[i]); }
          children[i].delete();
        }
      }
    }
  }

  /** Delete given directory
   * @param directory directory which will be delete
   */
  public static void deleteDirectory(final File directory) {
    FileUtil.cleanDirectory(directory);
    directory.delete();
  }
}
