/*
    Copyright 2013 KU Leuven Research and Development - iMinds - Distrinet

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    Administrative Contact: dnet-project-office@cs.kuleuven.be
    Technical Contact: bart.vanbrabant@cs.kuleuven.be
 */

package drm.taskworker.workers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.cassandra.io.util.FileUtils;
import org.apache.commons.io.IOUtils;

import static drm.taskworker.workers.Constants.*;
import drm.taskworker.Worker;
import drm.taskworker.tasks.ParameterFoundException;
import drm.taskworker.tasks.Task;
import drm.taskworker.tasks.TaskResult;
import drm.taskworker.tasks.ValueRef;

/**
 * The optimus worker coordinates the execution of a computational command. The
 * worker always requires a 'command' to be an argument.
 * 
 * Parameters:
 * 
 * @param: command The command to execute
 * @param: a The start value of the interval
 * @param: b The end value of the interval
 * @param: d The distance between a and b that indicates we can stop searching
 * 
 *         The number of tasks required to search is determined as following n =
 *         int(abs(a-b)/d)
 * 
 * @author Bart Vanbrabant <bart.vanbrabant@cs.kuleuven.be>
 */
public class OptimusWorker2 extends Worker {

	/**
	 * Creates a new work with the name blob-to-cache
	 */
	public OptimusWorker2(String workerName) {
		super(workerName);
	}

	private Map<UUID, Process> handles = new HashMap<>();

	/**
	 * Archive the result of the previous task
	 */
	@SuppressWarnings("unchecked")
	public TaskResult work(Task task) {
		try {
			String type = (String) task.getParam("type");

			switch (type) {
			case TYPE_START:
				return start(task, (byte[]) task.getParam(PRM_START_FILE),
						(String) task.getParam(PRM_START_METHOD));
			case TYPE_STEP:
				return step(task, (List<ValueRef>) task.getParam("result"),(List<ValueRef>) task.getParam("name"));
			case TYPE_STOP:
				return stop(task);
			}
			TaskResult out = new TaskResult();
			out.setException(new IllegalArgumentException(type));
			out.fail();
			return out;
		} catch (Exception e) {
			TaskResult out = new TaskResult();
			out.setException(e);
			out.fail();
			return out;
		}

	}

	private TaskResult stop(Task task) {
		//don't wait for kill
		
		handles.remove(task.getId()).destroy();
		return new TaskResult();
	}

	private TaskResult step(Task task, List<ValueRef> results, List<ValueRef> names) throws IOException, ParameterFoundException {
		//write out outputs
		File workdir = new File(task.getJobId().toString());
		for(int i =0;i<results.size();i++){
			output(workdir,(byte[])results.get(i).getValue(),(String)names.get(i).getValue());
		}
		return stop(task);
	}

	

	private void output(File workdir, byte[] value, String name) throws IOException {
		OutputStream os = new FileOutputStream(new File(workdir,name));
		os.write(value);
		os.close();
		
	}

	private TaskResult start(Task task, byte[] file, String method) throws IOException {
		
		File workdir = new File(task.getJobId().toString());
		FileUtils.createDirectory(workdir);
		
		File f = new File(workdir,"input");
		OutputStream out = new FileOutputStream(f);
		out.write(file);
		out.close();
		
		ProcessBuilder pb = new ProcessBuilder("optimus",method);
		pb.directory(workdir);
		
		Process handle = pb.start();
		 
		(new VoidStreamPump(handle.getInputStream())).start();
		(new VoidStreamPump(handle.getErrorStream())).start();
		
		handles.put(task.getJobId(), handle);
		
		return waitForIt(task,workdir);
	}

	private TaskResult waitForIt(Task task,File workdir) throws IOException {
		File gofile = new File(workdir,"dreamaas.go");
		while(!gofile.exists()){
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				throw new Error("should not occur",e);
			}
		}
		
		TaskResult out = new TaskResult();
		
		File runfile = new File(workdir,"runfile");
		BufferedReader br = new BufferedReader(new FileReader(runfile));
		String line = br.readLine();
		while(line!=null){
			
			out.addNextTask(makeTask(task,workdir,line));
			
			line = br.readLine();
			
		}
		br.close();
		return out;
	}

	private Task makeTask(Task task, File workdir, String line) throws IOException {
		String[] parts = line.split("|");
		String command = parts[0];
		String inputfile = parts[1];
		String outputfile = parts[2];
		
		FileInputStream fis = new FileInputStream(new File(workdir, inputfile));
		byte[] outputData = IOUtils.toByteArray(fis);
		
		Task out = new Task(task, "execute");
		out.addParam("command", command);
		out.addParam("input", outputData);
		out.addParam("output", outputfile);
		
		return task;
	}
}
