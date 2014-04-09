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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Map;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import drm.taskworker.Worker;
import drm.taskworker.tasks.Task;
import drm.taskworker.tasks.TaskResult;

/**
 * A worker that renders a template
 *
 * @author Bart Vanbrabant <bart.vanbrabant@cs.kuleuven.be>
 */
public class TemplateWorker extends Worker {
	public TemplateWorker(String workerName) {
		super(workerName);
	}

	@Override
	public TaskResult work(Task task) {
		TaskResult result = new TaskResult();

		try {
			VelocityEngine ve = new VelocityEngine();
            ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
            ve.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
            ve.setProperty("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.NullLogSystem");
            ve.init();

            final String templatePath = "invoice-template.xsl";
            InputStream input = this.getClass().getClassLoader().getResourceAsStream(templatePath);
            if (input == null) {
                throw new IOException("Template file doesn't exist");
            }

            Task newTask = new Task(task, this.getNextWorker(task.getJobId()));
            // FIXME should this writer be in the loop and be closed there or can it be reused?
            for(String tag: task.getParamNames()) {
            	if(tag != null && tag.startsWith("Doc#")) {
            		StringWriter writer = new StringWriter();
            		
            		Map<String, String> doc = (Map<String, String>) task.getParam(tag);
            		
            		VelocityContext context = new VelocityContext();
            		for (String header: doc.keySet()) {
        				context.put(header, doc.get(header));
        			}
            		
            		Template template = ve.getTemplate(templatePath, "UTF-8");
                    
        			
        			template.merge(context, writer);
        			writer.flush();
        			
        			newTask.addParam(tag, writer.toString());
        			
        			writer.close();
            	} else if(tag != null && tag.equals("BatchNb")) {
            		newTask.addParam("BatchNb", task.getParam("BatchNb"));
            	}
            }
            
            result.addNextTask(newTask);
            result.setResult(TaskResult.Result.SUCCESS);			
		} catch (Exception e) {
			result.setResult(TaskResult.Result.EXCEPTION);
			result.setException(e);
			return result;
		}
		
		return result;
	}
}