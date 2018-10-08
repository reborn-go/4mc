/**
    4MC
    Copyright (c) 2014, Carlo Medas
    BSD 2-Clause License (http://www.opensource.org/licenses/bsd-license.php)

    Redistribution and use in source and binary forms, with or without modification,
    are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, this
      list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above copyright notice, this
      list of conditions and the following disclaimer in the documentation and/or
      other materials provided with the distribution.

    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
    ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
    WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
    DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
    ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
    (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
    LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
    ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
    (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
    SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

  You can contact 4MC author at :
      - 4MC source repository : https://github.com/carlomedas/4mc

  LZ4 - Copyright (C) 2011-2014, Yann Collet - BSD 2-Clause License.
  You can contact LZ4 lib author at :
      - LZ4 source repository : http://code.google.com/p/lz4/
 **/
package com.hadoop.mapred;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionCodecFactory;
import org.apache.hadoop.mapred.FileSplit;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.util.LineReader;

import java.io.IOException;

/**
 * Reads line from a 4mc compressed text file. Treats keys as offset in file and
 * value as line.
 */
public class FourMcLineRecordReader implements RecordReader<Text, Text> {

	public static final String MAX_LINE_LEN_CONF = "com.hadoop.mapred.fourmc.line.recordreader.max.line.length";

	private long start;
	private long pos;
	private long end;
	private LineReader in;
	private FSDataInputStream fileIn;

	private int maxLineLen = Integer.MAX_VALUE;

	public String getName() {
		return "fourmc";
	}

	public String getFilePattern() {
		return "\\.4mc&";
	}

	@Override
	public Text createKey() {
		return new Text();
	}

	@Override
	public Text createValue() {
		return new Text();
	}

	/**
	 * Get the progress within the split.
	 */
	@Override
	public float getProgress() {
		if (start == end) {
			return 0.0f;
		} else {
			return Math.min(1.0f, (pos - start) / (float) (end - start));
		}
	}

	@Override
	public synchronized long getPos() throws IOException {
		return pos;
	}

	@Override
	public synchronized void close() throws IOException {
		if (in != null) {
			in.close();
		}
	}

	public FourMcLineRecordReader(InputSplit genericSplit, JobConf job)
			throws IOException, InterruptedException {
		FileSplit split = (FileSplit) genericSplit;
		start = split.getStart();
		end = start + split.getLength();
		final Path file = split.getPath();
		maxLineLen = job.getInt(MAX_LINE_LEN_CONF, Integer.MAX_VALUE);

		FileSystem fs = file.getFileSystem(job);
		CompressionCodecFactory compressionCodecs = new CompressionCodecFactory(
				job);
		final CompressionCodec codec = compressionCodecs.getCodec(file);
		if (codec == null) {
			throw new IOException("Codec for file " + file
					+ " not found, cannot run");
		}

		// open the file and seek to the start of the split
		fileIn = fs.open(split.getPath());

		// creates input stream and also reads the file header
		in = new LineReader(codec.createInputStream(fileIn), job);

		if (start != 0) {
			fileIn.seek(start);

			// read and ignore the first line
			in.readLine(new Text());
			start = fileIn.getPos();
		}

		this.pos = start;
	}

	@Override
	public boolean next(Text key, Text value) throws IOException {
		// exactly same as EB one
		if (pos <= end) {
			int newSize = in.readLine(value, maxLineLen);
			if (newSize == 0) {
				return false;
			}
			pos = fileIn.getPos();
			
			//set dummy key
			key.set("");
			return true;
		}
		return false;
	}

}
