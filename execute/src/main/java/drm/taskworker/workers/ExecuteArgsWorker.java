/*
    Copyright 2014 KU Leuven Research and Development - iMinds - Distrinet

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
 * The "input" parameter is placed in an input file and passed as the first parameter of the command
 * 
 * The output data is collected from the filename that is passed as the second parameter
 * 
 * @author Bart Vanbrabant <bart.vanbrabant@cs.kuleuven.be>
 */
public class ExecuteArgsWorker extends Worker {
	/**
	 * Creates a new worker
	 */
	public ExecuteArgsWorker(String workerName) {
		super(workerName);
	}

	/**
	 * Archive the result of the previous task
	 */
	public TaskResult work(Task task) {
		logger.info("Executing with arguments");
		TaskResult result = new TaskResult();
		
		String command = null;
		byte[] inputData = null;
		try {
			command = (String)task.getParam("command");
			inputData = (byte[])task.getParam("input");
		} catch (ParameterFoundException e) {
			return result.setResult(TaskResult.Result.ARGUMENT_ERROR);
		}
		
		Set<String> names = task.getParamNames();
		
		try {
			// create the temp files, and execute the command
		    File inputFile = File.createTempFile("execute", "dat");
		    File outputFile = File.createTempFile("execute", "dat");
		    
		    String[] exe = { command, inputFile.getAbsolutePath(), outputFile.getAbsolutePath() };
		    
		    FileOutputStream fos = new FileOutputStream(inputFile);
		    fos.write(inputData);
		    fos.close();

		    Process p = Runtime.getRuntime().exec(exe);
		    p.waitFor();
		    
			// read the output data back and report
			FileInputStream fis = new FileInputStream(outputFile);
			byte[] outputData = IOUtils.toByteArray(fis);
			
			Task newTask = new Task(task, this.getNextWorker(task.getJobId()));
			newTask.addParam("output", outputData);
			
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
