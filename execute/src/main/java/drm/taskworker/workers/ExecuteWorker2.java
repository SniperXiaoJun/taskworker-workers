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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import drm.taskworker.Worker;
import drm.taskworker.tasks.ParameterFoundException;
import drm.taskworker.tasks.Task;
import drm.taskworker.tasks.TaskResult;

/**
 * Execute a command, collect its output and send it to the next task.
 * 
 * The input data is placed in a file of which the location is placed in
 * INPUT_FILE environment variable.
 * 
 * The output data is collected from the filename that is placed in the 
 * environment variable OUTPUT_FILE.
 * 
 * @author Bart Vanbrabant <bart.vanbrabant@cs.kuleuven.be>
 */
public class ExecuteWorker2 extends Worker {
	/**
	 * Creates a new work with the name blob-to-cache
	 */
	public ExecuteWorker2(String workerName) {
		super(workerName);
	}

	/**
	 * Archive the result of the previous task
	 */
	public TaskResult work(Task task) {
		logger.info("Executing");
		TaskResult result = new TaskResult();
		
		String command = null;
		byte[] inarg = null;
		String outarg = null;
		try {
			command = (String)task.getParam("command");
			inarg = (byte[] )task.getParam("input");
			outarg = (String)task.getParam("output");
		} catch (ParameterFoundException e) {
			return result.setResult(TaskResult.Result.ARGUMENT_ERROR);
		}
		
		Set<String> names = task.getParamNames();
		
		try {
			// create the temp files, and execute the command
		    String[] exe = { command };
		    
		    File inputFile = File.createTempFile("execute", "dat");
		    File outputFile = File.createTempFile("execute", "dat");
		    
		    FileOutputStream fos = new FileOutputStream(inputFile);
		    fos.write(inarg);
		    fos.close();

		    ProcessBuilder pb = new ProcessBuilder(command,inputFile.getAbsolutePath(),outputFile.getAbsolutePath());
		    pb.start().waitFor();
		    
		    
			// read the output data back and report
			FileInputStream fis = new FileInputStream(outputFile);
			byte[] outputData = IOUtils.toByteArray(fis);
			
			Task newTask = new Task(task, this.getNextWorker(task.getJobId()));
			newTask.addParam("result", outputData);
			newTask.addParam("name", outarg);
			
			// also add original parameters
		    for (String name : names) {
		    	try {
			    	newTask.addParam(name, task.getParam(name));
				} catch (ParameterFoundException e) {
					// cannot happen
				}
		    }
			
			result.addNextTask(newTask);
			result.setResult(TaskResult.Result.SUCCESS);
			
		} catch (IOException | InterruptedException e) {
			result.setResult(TaskResult.Result.EXCEPTION);
			result.setException(e);
		}

		return result;
	}
}
