import java.io.IOException;
import java.util.StringTokenizer;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.KeyValueTextInputFormat;
import org.apache.hadoop.mapreduce.lib.jobcontrol.ControlledJob;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.mapred.jobcontrol.JobControl;
import java.nio.ByteBuffer;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.WritableComparator;
import java.io.IOException;
import java.util.StringTokenizer;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import java.io.IOException;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

public class WordCountDriver extends Configured implements Tool {

    public int run(String[] args) throws Exception {
        JobControl jobControl = new JobControl("jobChain");
        Configuration conf1 = getConf();

        Job job1 = Job.getInstance(conf1);
        job1.setJarByClass(WordCountDriver.class);
        job1.setJobName("unigram count");

        FileInputFormat.setInputPaths(job1, new Path(args[0]));
        FileOutputFormat.setOutputPath(job1, new Path(args[1] + "/temp"));

        job1.setMapperClass(WordCountMapper.class);
        job1.setReducerClass(WordCountReducer.class);
        job1.setCombinerClass(WordCountReducer.class);

        job1.setOutputKeyClass(Text.class);
        job1.setOutputValueClass(IntWritable.class);

        ControlledJob controlledJob1 = new ControlledJob(conf1);
        controlledJob1.setJob(job1);

        jobControl.addJob(controlledJob1);
        Configuration conf2 = getConf();

        Job job2 = Job.getInstance(conf2);
        job2.setJarByClass(WordCountDriver.class);
        job2.setJobName("unigram descending");

        FileInputFormat.setInputPaths(job2, new Path(args[1] + "/temp"));
        FileOutputFormat.setOutputPath(job2, new Path(args[1] + "/output"));

        job2.setMapperClass(WordCountMapper2.class);
        job2.setReducerClass(WordCountReducer2.class);
        job2.setCombinerClass(WordCountReducer2.class);

        job2.setOutputKeyClass(IntWritable.class);
        job2.setOutputValueClass(Text.class);
        job2.setInputFormatClass(KeyValueTextInputFormat.class);

        job2.setSortComparatorClass(IntComparator.class);
        ControlledJob controlledJob2 = new ControlledJob(conf2);
        controlledJob2.setJob(job2);

        // make job2 dependent on job1
        controlledJob2.addDependingJob(controlledJob1);
        // add the job to the job control
        jobControl.addJob(controlledJob2);
        Thread jobControlThread = new Thread(jobControl);
        jobControlThread.start();

        while (!jobControl.allFinished()) {
            System.out.println("Jobs in waiting state: " + jobControl.getWaitingJobList().size());
            System.out.println("Jobs in ready state: " + jobControl.getReadyJobsList().size());
            System.out.println("Jobs in running state: " + jobControl.getRunningJobList().size());
            System.out.println("Jobs in success state: " + jobControl.getSuccessfulJobList().size());
            System.out.println("Jobs in failed state: " + jobControl.getFailedJobList().size());
            try {
                Thread.sleep(5000);
            } catch (Exception e) {

            }

        }
        System.exit(0);
        return (job1.waitForCompletion(true) ? 0 : 1);
    }

    public static void main(String[] args) throws Exception {
        int exitCode = ToolRunner.run(new WordCountDriver(), args);
        System.exit(exitCode);
    }

    public static class IntComparator extends WritableComparator {

        public IntComparator() {
            super(IntWritable.class);
        }

        @Override
        public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
            Integer v1 = ByteBuffer.wrap(b1, s1, l1).getInt();
            Integer v2 = ByteBuffer.wrap(b2, s2, l2).getInt();
            return v1.compareTo(v2) * (-1);
        }
    }

    public static class WordCountMapper extends Mapper<LongWritable, Text, Text, IntWritable> {
        IntWritable one = new IntWritable(1);
        Text unigram = new Text();

        @Override
        public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
            StringTokenizer token = new StringTokenizer(
                    value.toString().replaceAll("\\p{Punct}", "").replaceAll("[0-9]", "").toLowerCase());

            while (token.hasMoreTokens()) {
                unigram.set(token.nextToken());
                context.write(unigram, one);
            }
        }
    }

    public static class WordCountReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
        @Override
        public void reduce(Text key, Iterable<IntWritable> values, Context context)
                throws IOException, InterruptedException {
            int count = 0;
            for (IntWritable value : values) {
                count += value.get();

            }
            context.write(key, new IntWritable(count));
        }
    }

    public static class WordCountMapper2 extends Mapper<Text, Text, IntWritable, Text> {

        IntWritable frequency = new IntWritable();

        @Override
        public void map(Text key, Text value, Context context) throws IOException, InterruptedException {

            int newVal = Integer.parseInt(value.toString());
            frequency.set(newVal);
            context.write(frequency, key);
        }
    }

    public static class WordCountReducer2 extends Reducer<IntWritable, Text, IntWritable, Text> {

        Text word = new Text();

        @Override
        public void reduce(IntWritable key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {

            for (Text value : values) {
                word = value;
                context.write(key, word);
            }
        }
    }
}
