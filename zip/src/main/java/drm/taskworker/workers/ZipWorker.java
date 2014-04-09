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

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import drm.taskworker.Worker;
import drm.taskworker.tasks.ParameterFoundException;
import drm.taskworker.tasks.Task;
import drm.taskworker.tasks.TaskResult;
import drm.taskworker.tasks.ValueRef;

/**
 * Collect all files in a workflow and zip them when an end of workflow task
 * is received.
 * 
 * @author Bart Vanbrabant <bart.vanbrabant@cs.kuleuven.be>
 */
public class ZipWorker extends Worker {
	
	/**
	 * Creates a new work with the name blob-to-cache
	 */
	public ZipWorker(String workerName) {
		super(workerName);
	}

	/**
	 * Zip all files
	 */
	public TaskResult work(Task task) {
		TaskResult result = new TaskResult();
		
		try {
		
		// create the zip stream
		ByteArrayOutputStream boas = new ByteArrayOutputStream();
		ZipOutputStream out = new ZipOutputStream(boas);
		
		for(String tag : task.getParamNames()) {
			if(tag != null && tag.startsWith("Doc#")) {
				// paramNames for upnxt workflow
				zip(out, task, tag);
			} else if(tag != null && tag.equals("arg0")) {
				// paramNames for other workflows
				zip(out, task, tag);
				break;
			}
		}
		
		out.close();
		boas.flush();
		
		byte[] zipData = boas.toByteArray();
		boas.close();
		
		Task newTask = new Task(task, this.getNextWorker(task.getJobId()));
		newTask.addParam("arg0", zipData);
		result.addNextTask(newTask);
		
		} catch (FileNotFoundException e) {
			result.setResult(TaskResult.Result.EXCEPTION);
			result.setException(e);
		} catch (IOException e) {
			result.setResult(TaskResult.Result.EXCEPTION);
			result.setException(e);
		} catch (ParameterFoundException e) {
			result.setResult(TaskResult.Result.ARGUMENT_ERROR);
			result.setException(e);
		}

		return result.setResult(TaskResult.Result.SUCCESS);
	}
	
	@SuppressWarnings("unchecked")
	private void zip(ZipOutputStream zipOut, Task task, String tag) throws ParameterFoundException, IOException {
		List<ValueRef> data = null;
		
		data = (List<ValueRef>) task.getParam(tag);
		
		if (data.size() == 0) {
			logger.warning("empty zip file");
		}
			
		// create the zip stream
		ByteArrayOutputStream boas = new ByteArrayOutputStream();
		ZipOutputStream out = new ZipOutputStream(boas);

		// save the files in the zip
		int i = 0;
		for (ValueRef ref : data) {
			out.putNextEntry(new ZipEntry(++i + ".pdf"));
			byte[] pdfData = (byte[])ref.getValue();
			out.write(pdfData);
		}
	}
}