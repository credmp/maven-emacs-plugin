package org.apache.maven.plugin.jdee.support;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

/** Class which can write information about class
 * @author <a href="mailto:bendal@apnet.cz">Lukas Benda</a>
 * @version 1.0
 */
public class ClassBrowser {

  /** Methode which return as string all methode from class
   * @return string with describe all methode in class
   */
  public static String allClassMethode(Class clazz) {
    StringBuffer result = new StringBuffer();
    while (!clazz.equals(Object.class)) {
      result.append("Methodes of class: " + clazz.getName());
      result.append("\n");
      for (Method method : clazz.getDeclaredMethods()) {
        String s = "   " + Modifier.toString(method.getModifiers())
          + " " + method.getGenericReturnType().toString()
          + " " + method.getName() + "(";
        for (int i = 0; i < method.getGenericParameterTypes().length; i++) {
          s += method.getGenericParameterTypes()[i].toString();
          if (i + 1 < method.getGenericParameterTypes().length) {
            s += ", ";
          }
        }
        s += ")";
        if (method.getGenericExceptionTypes() != null
            && method.getGenericExceptionTypes().length > 0) {
          s += " throws ";
          for (Type type : method.getGenericExceptionTypes()) {
            s += type.toString() + ", ";
          }
        }
        result.append(s);
        result.append("\n");
      }
      clazz = clazz.getSuperclass();
    }
    return result.toString();
  }
}
