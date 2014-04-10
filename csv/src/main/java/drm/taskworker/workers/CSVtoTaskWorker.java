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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import au.com.bytecode.opencsv.CSVReader;
import drm.taskworker.Worker;
import drm.taskworker.tasks.ParameterFoundException;
import drm.taskworker.tasks.Task;
import drm.taskworker.tasks.TaskResult;

/**
 * A worker that takes a csv file as input and generates a new task for each 
 * row. Each record in a row is added as a parameter to a task.
 * 
 * @author Bart Vanbrabant <bart.vanbrabant@cs.kuleuven.be>
 */
public class CSVtoTaskWorker extends Worker {
	public CSVtoTaskWorker(String workerName) {
		super(workerName);
	}

	@Override
	public TaskResult work(Task task) {
		TaskResult result = new TaskResult();
		
		String csv_data = null;
		try {
			csv_data = (String)task.getParam("arg0");
			assert(csv_data != null);
		} catch (ParameterFoundException e) {
			return result.setResult(TaskResult.Result.ARGUMENT_ERROR);
		}
		
		// read in the csv
		try {
			CSVReader parser = new CSVReader(new StringReader(csv_data), ';');
			List<String[]> rows = parser.readAll();
			String[] headers = rows.get(0);
			int batchSize = 1;
			try{ 
				batchSize = Integer.valueOf(task.getJobOption("batch.size"));
			} catch(Exception e) {
				// keep batchSize == 1
			}

			logger.info(String.format("Parsed %d records in CSV", rows.size() - 1));
			int batchNb = 1;
			for (int i = 1; i < rows.size(); i = i+batchSize) {
				
				Task newTask = new Task(task, this.getNextWorker(task.getJobId()));
				
				for(int d = 0; (d < batchSize && i+d < rows.size()); d++) {
					String[] row = rows.get(i+d);
					
					Map<String, String> document = new HashMap<String, String>();
					for (int r = 0; r < row.length; r++) {
						document.put(headers[r], row[r]);
					}
					int docNb = d + 1;
					newTask.addParam("Doc#"+docNb, document);
				}
				newTask.addParam("BatchNb", batchNb);
				
				result.addNextTask(newTask);
				batchNb++;
			}
			parser.close();
			
			result.setSplit();
			
			result.setResult(TaskResult.Result.SUCCESS);
		} catch (FileNotFoundException e) {
			result.setResult(TaskResult.Result.EXCEPTION);
			result.setException(e);
		} catch (IOException e) {
			result.setResult(TaskResult.Result.EXCEPTION);
			result.setException(e);
		}
		
		return result;
	}
}