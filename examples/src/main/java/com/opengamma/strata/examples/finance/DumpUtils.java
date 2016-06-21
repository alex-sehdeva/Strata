package com.opengamma.strata.examples.finance;

import java.io.FileWriter;
import java.io.IOException;

import org.joda.beans.Bean;
import org.joda.beans.ser.JodaBeanSer;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class DumpUtils {
  
  private static void beanDump(Bean bean, String outputPath) {
    FileWriter writer;      
    try {   
      writer = new FileWriter(outputPath);  
      writer.write(JodaBeanSer.PRETTY.jsonWriter().write(bean));
      } catch (IOException e) {e.printStackTrace();}    
  }
  
  private static void dump(Object dumpObject, String outputPath) {
    
    final DumperOptions yamlFormat = new DumperOptions();     
    yamlFormat.setDefaultFlowStyle(DumperOptions.FlowStyle.AUTO);     
    yamlFormat.setWidth(Integer.MAX_VALUE);     
    final Yaml yaml = new Yaml(yamlFormat);     
    FileWriter writer;      
    try {   
      writer = new FileWriter(outputPath);  
      yaml.dump(dumpObject, writer);  
      } catch (IOException e) {   
        // TODO Auto-generated catch block  
        e.printStackTrace();  
        }    
  }

}
