/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
//import org.apache.lucene.document.TextField;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/** Index all text files under a directory.
 * <p>
 * This is a command-line application demonstrating simple Lucene indexing.
 * Run it with no command-line arguments for usage information.
 */
public class IndexFiles {
  
	protected static float anchorBoost = 0.8f;
	protected static float titleBoost = 1f;
	
	
	
  private IndexFiles() {}

  /** Index all text files under a directory. */
  public static void main(String[] args) {
    String usage = "java org.apache.lucene.demo.IndexFiles"
                 + " [-index INDEX_PATH] [-docs DOCS_PATH] [-update]\n\n"
                 + "This indexes the documents in DOCS_PATH, creating a Lucene index"
                 + "in INDEX_PATH that can be searched with SearchFiles";
    String indexPath = "index";
    String docsPath = null;
    boolean create = true;
    String type = "";
    
    for(int i=0;i<args.length;i++) {
      if ("-index".equals(args[i])) {
        indexPath = args[i+1];
        i++;
      } else if ("-docs".equals(args[i])) {
        docsPath = args[i+1];
        i++;
      } else if ("-update".equals(args[i])) {
        create = false;
      } else if ("-type".equals(args[i])){
    	type = args[i+1]; 
      }
    }
    
    if(type.contains("h")){
    	indexPath="HIndex";
    }else if(type.contains("a")){
    	indexPath="AIndex";
    }

    if (docsPath == null) {
      System.err.println("Usage: " + usage);
      System.exit(1);
    }

    final Path docDir = Paths.get(docsPath);
    if (!Files.isReadable(docDir)) {
      System.out.println("Document directory '" +docDir.toAbsolutePath()+ "' does not exist or is not readable, please check the path");
      System.exit(1);
    }
    
    Date start = new Date();
    try {
      System.out.println("Indexing to directory '" + indexPath + "'...");

      Directory dir = FSDirectory.open(Paths.get(indexPath));
      Analyzer analyzer = new StandardAnalyzer();
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

      if (create) {
        // Create a new index in the directory, removing any
        // previously indexed documents:
        iwc.setOpenMode(OpenMode.CREATE);
      } else {
        // Add new documents to an existing index:
        iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
      }

      // Optional: for better indexing performance, if you
      // are indexing many documents, increase the RAM
      // buffer.  But if you do this, increase the max heap
      // size to the JVM (eg add -Xmx512m or -Xmx1g):
      //
      // iwc.setRAMBufferSizeMB(256.0);
      
      String line;
      
      BufferedReader tbr = new BufferedReader(new InputStreamReader(new FileInputStream("wiki/id2url"), "UTF-8"));
      Map<String, String> map2 = new HashMap<String, String>();
	  
	  while ((line = tbr.readLine()) != null){
		  String [] sa = line.split(" ");
		  String [] sa1 = sa[1].split("/");
		  map2.put(sa[0], sa1[sa1.length-1]);
	  }

	  BufferedReader abr = new BufferedReader(new InputStreamReader(new FileInputStream("anchor.txt"), "UTF-8"));
	  Map<String, String> map0=new HashMap<String, String>();
	  
      if(type.contains("a")){
	      
		  
		  while ((line = abr.readLine()) != null){
			  String [] sa = line.split("\t");
			  map0.put(sa[0], sa[1]);
		  }
      }
      
      BufferedReader pbr = new BufferedReader(new InputStreamReader(new FileInputStream("pagerank.txt"), "UTF-8"));
      Map<String, Float> map1=new HashMap<String, Float>();
      
	  if(type.contains("h")){
		   while ((line = pbr.readLine()) != null){
			   String [] sa = line.split("\t");
			   map1.put(sa[0],  Float.parseFloat(sa[1]));
		   }
	  }
     
      IndexWriter writer = new IndexWriter(dir, iwc);
      indexDocs(writer, docDir, map0,map1,map2,type);

      // NOTE: if you want to maximize search performance,
      // you can optionally call forceMerge here.  This can be
      // a terribly costly operation, so generally it's only
      // worth it when your index is relatively static (ie
      // you're done adding documents to it):
      //
      // writer.forceMerge(1);

      writer.close();
      abr.close();
      tbr.close();
      pbr.close();
      
      Date end = new Date();
      System.out.println(end.getTime() - start.getTime() + " total milliseconds");

    } catch (IOException e) {
      System.out.println(" caught a " + e.getClass() +
       "\n with message: " + e.getMessage());
    }
  }

  /**
   * Indexes the given file using the given writer, or if a directory is given,
   * recurses over files and directories found under the given directory.
   * 
   * NOTE: This method indexes one document per input file.  This is slow.  For good
   * throughput, put multiple documents into your input file(s).  An example of this is
   * in the benchmark module, which can create "line doc" files, one document per line,
   * using the
   * <a href="../../../../../contrib-benchmark/org/apache/lucene/benchmark/byTask/tasks/WriteLineDocTask.html"
   * >WriteLineDocTask</a>.
   *  
   * @param writer Writer to the index where the given file/dir info will be stored
   * @param path The file to index, or the directory to recurse into to find files to index
   * @throws IOException If there is a low-level I/O error
   */
  static void indexDocs(final IndexWriter writer, Path path,final Map<String, String> amap,final Map<String,Float> pmap,final Map<String,String> tmap,final String type) throws IOException {
    if (Files.isDirectory(path)) {
      Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          try {
        	if(type.contains("h")){
        		indexDoc(writer, file, attrs.lastModifiedTime().toMillis(),amap,pmap,tmap);
        	}else if(type.contains("a")){
        		indexDoc(writer,file,attrs.lastModifiedTime().toMillis(),amap,tmap);
        	}else{
        		indexDoc(writer,file,attrs.lastModifiedTime().toMillis(),tmap);
        	}
            
          } catch (IOException ignore) {
            // don't index files that can't be read.
          }
          return FileVisitResult.CONTINUE;
        }
      });
    } else {
      indexDoc(writer, path, Files.getLastModifiedTime(path).toMillis(),amap,pmap,tmap);
    }
  }

  /** Indexes a single document */
  static void indexDoc(IndexWriter writer, Path file, long lastModified,Map<String, String> amap,Map<String,Float> pmap,Map<String,String> tmap) throws IOException {
    try (InputStream stream = Files.newInputStream(file)) {
      // make a new, empty document
      Document doc = new Document();
      System.out.println("H");
      boolean pos = true;
      FieldType ft = new FieldType();
      ft.setTokenized(true);
      //ft.setStored(true);
      if(pos){
	ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
      }else{
	ft.setIndexOptions(IndexOptions.DOCS);
      }
      // Add the path of the file as a field named "path".  Use a
      // field that is indexed (i.e. searchable), but don't tokenize 
      // the field into separate words and don't index term frequency
      // or positional information:
      System.out.println(file.toString());
      Field pathField = new StringField("path", file.toString(), Field.Store.YES);
      doc.add(pathField);
      
      String [] sa = file.toString().split("/");
      String id = sa[sa.length-1];
      //System.out.println(sa[sa.length-1]);
      
      float pr = pmap.get(id);
      
      
      
      Field anchorField = new TextField("anchor", amap.get(id), Field.Store.YES);
      anchorField.setBoost(anchorBoost*pr);
      doc.add(anchorField);
      
      Field titleField = new TextField("title",tmap.get(id),Field.Store.YES);
      titleField.setBoost(titleBoost*pr);
      doc.add(titleField);
      
      // Add the last modified date of the file a field named "modified".
      // Use a LongField that is indexed (i.e. efficiently filterable with
      // NumericRangeFilter).  This indexes to milli-second resolution, which
      // is often too fine.  You could instead create a number based on
      // year/month/day/hour/minutes/seconds, down the resolution you require.
      // For example the long value 2011021714 would mean
      // February 17, 2011, 2-3 PM.
      doc.add(new LongField("modified", lastModified, Field.Store.NO));
      
      // Add the contents of the file to a field named "contents".  Specify a Reader,
      // so that the text of the file is tokenized and indexed, but not stored.
      // Note that FileReader expects the file to be in UTF-8 encoding.
      // If that's not the case searching for special characters will fail.
      
      Field content = new Field("contents", new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)),ft);
      
      content.setBoost(pr);
      doc.add(content);
      
      //set doc.boost
      
      
      if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
        // New index, so we just add the document (no old document can be there):
        System.out.println("adding " + file);
        writer.addDocument(doc);
      } else {
        // Existing index (an old copy of this document may have been indexed) so 
        // we use updateDocument instead to replace the old one matching the exact 
        // path, if present:
        System.out.println("updating " + file);
        writer.updateDocument(new Term("path", file.toString()), doc);
      		}
    	}
   	}
    
    static void indexDoc(IndexWriter writer, Path file, long lastModified,Map<String,String> tmap) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
          // make a new, empty document
          Document doc = new Document();
          
          FieldType ft = new FieldType();
          ft.setTokenized(true);
          //ft.setStored(true);
          
          ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
          
          // Add the path of the file as a field named "path".  Use a
          // field that is indexed (i.e. searchable), but don't tokenize 
          // the field into separate words and don't index term frequency
          // or positional information:
          
          //System.out.println(file.toString());
          
          Field pathField = new StringField("path", file.toString(), Field.Store.YES);
          doc.add(pathField);
          
          String [] sa = file.toString().split("/");
          String id = sa[sa.length-1];
          
          //System.out.println(sa[sa.length-1]);
          
          /*
          Field anchorField = new TextField("anchor", amap.get(id), Field.Store.YES);
          anchorField.setBoost(1.2f);
          doc.add(anchorField);
          */
          
          Field titleField = new TextField("title",tmap.get(id),Field.Store.YES);
          titleField.setBoost(titleBoost);
          doc.add(titleField);
          
          // Add the last modified date of the file a field named "modified".
          // Use a LongField that is indexed (i.e. efficiently filterable with
          // NumericRangeFilter).  This indexes to milli-second resolution, which
          // is often too fine.  You could instead create a number based on
          // year/month/day/hour/minutes/seconds, down the resolution you require.
          // For example the long value 2011021714 would mean
          // February 17, 2011, 2-3 PM.
          doc.add(new LongField("modified", lastModified, Field.Store.NO));
          
          // Add the contents of the file to a field named "contents".  Specify a Reader,
          // so that the text of the file is tokenized and indexed, but not stored.
          // Note that FileReader expects the file to be in UTF-8 encoding.
          // If that's not the case searching for special characters will fail.
          
          Field content = new Field("contents", new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)),ft);
          
          //content.setBoost(1+pmap.get(id));
          
          doc.add(content);
          
          //set doc.boost
          
          
          if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
            // New index, so we just add the document (no old document can be there):
            System.out.println("adding " + file);
            writer.addDocument(doc);
          } else {
            // Existing index (an old copy of this document may have been indexed) so 
            // we use updateDocument instead to replace the old one matching the exact 
            // path, if present:
            System.out.println("updating " + file);
            writer.updateDocument(new Term("path", file.toString()), doc);
          }
        }
  }
    static void indexDoc(IndexWriter writer, Path file, long lastModified,Map<String,String> amap,Map<String,String> tmap) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
          // make a new, empty document
          Document doc = new Document();
          System.out.println("A");
          FieldType ft = new FieldType();
          ft.setTokenized(true);
          //ft.setStored(true);
          
          ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
          
          // Add the path of the file as a field named "path".  Use a
          // field that is indexed (i.e. searchable), but don't tokenize 
          // the field into separate words and don't index term frequency
          // or positional information:
          
          //System.out.println(file.toString());
          
          Field pathField = new StringField("path", file.toString(), Field.Store.YES);
          doc.add(pathField);
          
          String [] sa = file.toString().split("/");
          String id = sa[sa.length-1];
          
          //System.out.println(sa[sa.length-1]);
          
          
          Field anchorField = new TextField("anchor", amap.get(id), Field.Store.YES);
          anchorField.setBoost(anchorBoost);
          doc.add(anchorField);
          
          
          Field titleField = new TextField("title",tmap.get(id),Field.Store.YES);
          titleField.setBoost(titleBoost);
          doc.add(titleField);
          
          // Add the last modified date of the file a field named "modified".
          // Use a LongField that is indexed (i.e. efficiently filterable with
          // NumericRangeFilter).  This indexes to milli-second resolution, which
          // is often too fine.  You could instead create a number based on
          // year/month/day/hour/minutes/seconds, down the resolution you require.
          // For example the long value 2011021714 would mean
          // February 17, 2011, 2-3 PM.
          doc.add(new LongField("modified", lastModified, Field.Store.NO));
          
          // Add the contents of the file to a field named "contents".  Specify a Reader,
          // so that the text of the file is tokenized and indexed, but not stored.
          // Note that FileReader expects the file to be in UTF-8 encoding.
          // If that's not the case searching for special characters will fail.
          
          Field content = new Field("contents", new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)),ft);
          
          //content.setBoost(1+pmap.get(id));
          
          doc.add(content);
          
          //set doc.boost
          
          
          if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
            // New index, so we just add the document (no old document can be there):
            System.out.println("adding " + file);
            writer.addDocument(doc);
          } else {
            // Existing index (an old copy of this document may have been indexed) so 
            // we use updateDocument instead to replace the old one matching the exact 
            // path, if present:
            System.out.println("updating " + file);
            writer.updateDocument(new Term("path", file.toString()), doc);
          }
        }
  }
}
