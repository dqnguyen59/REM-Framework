/*
 * Copyright (C) 2023  Duy Quoc Nguyen <d.q.nguyen@smartblackbox.org> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 * 
 * File created on 01/01/2023
 */
package remframework.helpers;

import java.util.concurrent.atomic.AtomicLong;

public class PerformanceMonitor {
	
	private static final double ONE_MILLION_NANO_SECONDS = 1000000.0;

	public enum Measurement {
		physics,
		updateMatrix,
		sendToGPU,
		totalCPURenderTime,
		gui2D,
		totalRenderTime,
		bdfgbdfbD,
	}
	
	private static long[] iMeasurements = new long[Measurement.values().length];
	private static long[] measurements = new long[Measurement.values().length];

	public static int frames = 0;

	public static int totalFrames = 0;

	public static double fps = 0.0;

	private static final AtomicLong completed3DFrames = new AtomicLong();
	private static final double FPS_SMOOTHING = 0.2;

	public static void start(Measurement measurement) {
		iMeasurements[measurement.ordinal()] = System.nanoTime();
	}
	
	public static void stop(Measurement measurement) {
		iMeasurements[measurement.ordinal()] = System.nanoTime() - iMeasurements[measurement.ordinal()];
		measurements[measurement.ordinal()] = iMeasurements[measurement.ordinal()];
	}

	public static void record(Measurement measurement, long elapsedNanos) {
		measurements[measurement.ordinal()] = elapsedNanos;
	}
	
	public static double getMeasurement(Measurement measurement) {
		return measurements[measurement.ordinal()] / ONE_MILLION_NANO_SECONDS;
	}

	public static void reset(Measurement measurement) {
		measurements[measurement.ordinal()] = 0;
	}

	/** Records a frame that has completed in the independent 3D renderer. */
	public static void record3DFrame() {
		completed3DFrames.incrementAndGet();
	}

	/** Samples FPS from completed 3D publications, independent of the responsive 2D loop. */
	public static double sample3DFps(long elapsedNanos) {
		long frameCount = completed3DFrames.getAndSet(0);
		double instantaneous = elapsedNanos > 0
				? frameCount * Constants.NANOSECOND / elapsedNanos : 0.0;
		if (fps == 0.0) fps = instantaneous;
		else fps += FPS_SMOOTHING * (instantaneous - fps);
		return fps;
	}
}
