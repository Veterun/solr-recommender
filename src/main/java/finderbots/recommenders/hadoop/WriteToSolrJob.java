package finderbots.recommenders.hadoop;
/**
 * Licensed to Patrick J. Ferrel (PJF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. PJF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * If someone wants license or copyrights to this let me know
 * pat.ferrel@gmail.com
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * <p>Writes the DRMs passed in to Solr as csv files to a location in HDFS or the local file system. The Primary DRM is expected to be a item-item similarity matrix with Mahout internal ID. The Secondary DRM is from cross-action-similarities. It also needs a file containing a map of internal mahout IDs to external IDs--one for userIDs and one for itemIDs. It needs the location to put the similarity matrices, each will be put into a Solr fields of type 'string' for indexing.</p>
 * <p>The Solr csv files will be of the form:</p>
 * <p>item_id,similar_items,cross_action_similar_items</p>
 * <p> ipad,iphone,iphone nexus</p>
 * <p> iphone,ipad,ipad galaxy</p>
 * <p>todo: This is in-memory and single threaded. It's easy enough to mapreduce it but there would still have to be a shared in-memory BiMap per node. To remove the in-memory map a more complex data flow with joins needs to be implemented.</p>
 * <p>todo: Solr and LucidWorks Search support many stores for indexing. It might be nice to have a pluggable writer for different stores.</p>
 */

import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class WriteToSolrJob extends Configured implements Tool {
    private static Logger LOGGER = Logger.getRootLogger();

    private static Options options;

    @Override
    public int run(String[] args) throws Exception {
        options = new Options();
        CmdLineParser parser = new CmdLineParser(options);
        String s = options.toString();// for debuging ease

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
            return -1;
        }

        cleanOutputDirs(options);



        return 0;
    }

    private void cleanOutputDirs(Options options) throws IOException {
        FileSystem fs = FileSystem.get(getConf());
        //todo: instead of deleting all, delete only the ones we overwrite?
        Path outputDir = new Path(options.getOutputDir());
        try {
            fs.delete(outputDir, true);
        } catch (Exception e) {
            LOGGER.info("No output dir to delete, skipping.");
        }
    }

    public static void main(String[] args) throws Exception {
        ToolRunner.run(new Configuration(), new WriteToSolrJob(), args);
    }

    // Command line options for this job. Execute the main method above with no parameters
    // to get a help listing.
    //

    public class Options {

        //used by Solr
        private static final String DEFAULT_ITEM_ID_FIELD_NAME = "item_id";
        private static final String DEFAULT_USER_ID_FIELD_NAME = "user_id";
        private static final String DEFAULT_ITEM_SIMILARITY_FIELD_NAME = "similar_items";
        private static final String DEFAULT_CROSS_ITEM_SIMILARITY_FIELD_NAME = "cross_action_similar_items";
        private static final String DEFAULT_ITEM_INDEX_FILENAME = ActionSplitterJob.Options.DEFAULT_ITEM_INDEX_FILENAME;
        private static final String DEFAULT_USER_INDEX_FILENAME = ActionSplitterJob.Options.DEFAULT_USER_INDEX_FILENAME;
        private String itemSimilarityMatrixDir;//required
        private String crossSimilarityMatrixDir = "";//optional
        private String userHistoryMatrixDir;//required
        private String indexesDir;//required
        private String userIndexFilePath;
        private String itemIndexFilePath;
        private String outputDir;//required
        private String itemIdFieldName = DEFAULT_ITEM_ID_FIELD_NAME;
        private String userIdFieldName = DEFAULT_USER_ID_FIELD_NAME;
        private String itemSimilarityFieldName = DEFAULT_ITEM_SIMILARITY_FIELD_NAME;
        private String crossActionSimilarityFieldName = DEFAULT_CROSS_ITEM_SIMILARITY_FIELD_NAME;

        Options() {
        }

        public String getItemIdFieldName() {
            return itemIdFieldName;
        }

        public String getUserIdFieldName() {
            return userIdFieldName;
        }

        public String getItemSimilarityFieldName() {
            return itemSimilarityFieldName;
        }

        public String getCrossActionSimilarityFieldName() {
            return crossActionSimilarityFieldName;
        }

        public String getItemSimilarityMatrixDir() {
            return itemSimilarityMatrixDir;
        }

        @Option(name = "-ism", aliases = {"--itemSimilarityMatrixDir"}, usage = "Input directory containing the Mahout DistributedRowMatrix containing Item-Item similarities. Will be written to Solr.", required = true)
        public void setItemSimilarityMatrixDir(String itemSimilarityMatrixDir) {
            this.itemSimilarityMatrixDir = itemSimilarityMatrixDir;
        }

        public String getIndexesDir() {
            return indexesDir;
        }

        @Option(name = "-ix", aliases = {"--indexDir"}, usage = "Where to get user and item indexes.", required = true)
        public void setIndexesDir(String indexesDir) {
            this.indexesDir = indexesDir;
            if(this.userIndexFilePath == null)
                userIndexFilePath = new Path(indexesDir, DEFAULT_USER_INDEX_FILENAME).toString();
            if(this.itemIndexFilePath == null)
                itemIndexFilePath = new Path(indexesDir, DEFAULT_ITEM_INDEX_FILENAME).toString();
        }

        public String getCrossSimilarityMatrixDir() {
            return crossSimilarityMatrixDir;
        }

        @Option(name = "-csm", aliases = {"--crossSimilarityMatrixDir"}, usage = "Input directory containing the Mahout DistributedRowMatrix containing Item-Item cross-action similarities. Will be written to Solr.", required = true)
        public void setCrossSimilarityMatrixDir(String crossSimilarityMatrixDir) {
            this.crossSimilarityMatrixDir = crossSimilarityMatrixDir;
        }

        public String getUserIndexFilePath() {
            return userIndexFilePath;
        }

        @Option(name = "-uix", aliases = {"--userIndex"}, usage = "Input directory containing the serialized BiMap of Mahout ID <-> external ID.", required = true)
        public void setUserIndexFilePath(String userIndexFilePath) {
            this.userIndexFilePath = userIndexFilePath;
        }

        public String getItemIndexFilePath() {
            return itemIndexFilePath;
        }

        @Option(name = "-iix", aliases = {"--itemIndex"}, usage = "Input directory containing the serialized BiMap of Mahout ID <-> external ID.", required = true)
        public void setItemIndexFilePath(String itemIndexFilePath) {
            this.itemIndexFilePath = itemIndexFilePath;
        }

        public String getOutputDir() {
            return outputDir;
        }

        @Option(name = "-o", aliases = {"--output"}, usage = "Where to write docs of ids for indexing.", required = true)
        public void setOutputDir(String outputDir) {
            this.outputDir = outputDir;
        }

/* not needed?
        @Option(name = "-t", aliases = {"--tempDir"}, usage = "Place for intermediate data. Things left after the jobs but erased before starting new ones.", required = false)
        public void setTempDir(String tempDir) {
            this.tempDir = tempDir;
        }

        public String getTempDir() {
            return this.tempDir;
        }

        */

        @Override
        public String toString() {
            String options = ReflectionToStringBuilder.toString(this, ToStringStyle.MULTI_LINE_STYLE);
            options = options.replaceAll("\n", "\n#");
            Date date = new Date();
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy h:mm:ss a");
            String formattedDate = sdf.format(date);
            options = options + "\n# Timestamp for data creation = " + formattedDate;
            return options = new StringBuffer(options).insert(0, "#").toString();
        }
    }

}
