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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import drm.taskworker.Job;
import drm.taskworker.Worker;
import drm.taskworker.tasks.ParameterFoundException;
import drm.taskworker.tasks.Task;
import drm.taskworker.tasks.TaskResult;

/**
 * A generic worker that joins a workflow by collecting all tasks of a workflow
 * and sending out a new task with all previous tasks in it.
 * 
 * This worker collects all arguments of the joined tasks and sends out a new
 * task with the same arguments in list form.
 * 
 * This worker can only be used once in a workflow with the same worker name.
 * 
 * @author Bart Vanbrabant <bart.vanbrabant@cs.kuleuven.be>
 */
public class JoinWorker extends Worker {
	protected static final Logger logger = Logger.getLogger(JoinWorker.class
			.getCanonicalName());

	/**
	 * Creates a new worker
	 */
	public JoinWorker(String workerName) {
		super(workerName);
	}

	@Override
	public TaskResult work(Task task) {
		TaskResult result = new TaskResult();

		try {
			// get the last join id from the queue (String)
			String joinQueue = (String) task.getParam(Task.JOIN_PARAM);
			if (joinQueue.length() % 37 != 0) {
				throw new IllegalArgumentException("Invalid JOIN LIST: "
						+ joinQueue);
			}

			UUID joinId = UUID.fromString(joinQueue.substring(
					joinQueue.length() - 36, joinQueue.length()));
			joinQueue = joinQueue.substring(0, joinQueue.length() - 37);

			// decrement the join counter
			Job.decrementJoin(task.getJobId(), joinId);

			// get its current value
			int joinValue = Job.getJoinCount(task.getJobId(), joinId);

			// register this task as a parent of the future joined task
			Task.saveParent(task.getJobId(), joinId, task.getId());

			// if the joinValue is zero, we need to "materialize" the join task
			// WARNING: creating this task has to be idempotent because
			// retrieving
			// the join counter has a race, so two possible tasks are joining
			if (joinValue == 0) {
				// GO!
				Task newTask = new Task(task.getJobId(), joinId,
						this.getNextWorker(task.getJobId()));

				// load all parents and build the map of parameters
				Map<String, List<Object>> varMap = new HashMap<>();
				List<Task> parents = newTask.getParents();
				logger.info("Joining the results of " + parents.size()
						+ " parents");

				for (Task parentTask : parents) {
					for (String paramName : parentTask.getParamNames()) {
						if (!varMap.containsKey(paramName)) {
							varMap.put(paramName, new ArrayList<Object>());
						}
						varMap.get(paramName).add(
								parentTask.getParamRef(paramName));
					}
				}

				// put the param maps in the new task
				for (String varName : varMap.keySet()) {
					newTask.addParam(varName, varMap.get(varName));
				}

				// add the new join queue
				newTask.addParam(Task.JOIN_PARAM, joinQueue);

				// return the new task
				result.addNextTask(newTask);
			}

		} catch (ParameterFoundException e) {
			result.setException(e);
			result.setResult(TaskResult.Result.EXCEPTION);
			return result;
		}

		result.setResult(TaskResult.Result.SUCCESS);
		return result;
	}
}
