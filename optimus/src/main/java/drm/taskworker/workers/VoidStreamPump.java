package drm.taskworker.workers;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.yaml.snakeyaml.reader.StreamReader;

public class VoidStreamPump extends StreamPump {

	public VoidStreamPump(InputStream s) {
		super(new BufferedReader(new InputStreamReader(s)));
	}

	@Override
	protected void dispatch(String s) {
		System.out.println(s);

	}

}
