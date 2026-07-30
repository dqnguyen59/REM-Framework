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
package remframework;

import org.lwjgl.Version;
import remframework.helpers.Constants;
import remframework.opengl.controller.Engine;
import remframework.opengl.controller.Simulator;
import remframework.opengl.view.GLWindow;
import remframework.settings.AppSettings;
import remframework.helpers.AppInfo;
import remframework.helpers.Utils;
import remframework.settings.Project;
import remframework.lattice.LatticeManager;
import remframework.geometry.discovery.GeometryDiscoveryWorkspaceManager;

import java.nio.file.Path;

public class Main {

    public static void main(String[] args) {
		Path startupProjectPath = null;
		Long updateLimit = null;
		Long frameLimit = null;
		boolean openGeometryDiscovery = false;
		for (String argument : args) {
			if (argument.startsWith("--")) {
				if (argument.equals("--geometry-discovery")) {
					openGeometryDiscovery = true;
					continue;
				}
				if (argument.startsWith("--frames=")) {
					frameLimit = Long.parseLong(argument.substring("--frames=".length()));
					if (frameLimit <= 0) throw new IllegalArgumentException(
							"--frames must be a positive integer.");
					continue;
				}
				if (!argument.startsWith("--updates=") || argument.length() == "--updates=".length()) {
					System.err.println("Invalid option: " + argument
							+ ". Expected --updates=<positive integer>.");
					System.exit(1);
					return;
				}
				if (updateLimit != null) {
					System.err.println("The --updates option may only be specified once.");
					System.exit(1);
					return;
				}
				try {
					updateLimit = Long.parseLong(argument.substring("--updates=".length()));
				}
				catch (NumberFormatException exception) {
					System.err.println("Invalid update limit: " + argument
							+ ". Expected a positive integer.");
					System.exit(1);
					return;
				}
				if (updateLimit <= 0) {
					System.err.println("Invalid update limit: " + argument
							+ ". Expected a positive integer.");
					System.exit(1);
					return;
				}
			}
			else {
				if (startupProjectPath != null) {
					System.err.println("Only one project filename may be specified.");
					System.exit(1);
					return;
				}
				startupProjectPath = Path.of(argument).toAbsolutePath().normalize();
			}
		}

		AppInfo.getManifestInfo("QuantumFieldSimulator");

		System.out.println(AppInfo.getTitle());
		System.out.println(AppInfo.getCopyRight());
		System.out.println("Version: " + AppInfo.getVersion());
		System.out.println("Version Date: " + AppInfo.getDateTime());
		System.out.println("Built By: " + AppInfo.getBuiltBy());
		System.out.println("Author: " + AppInfo.getAuthor());
		System.out.println("Contributors: " + AppInfo.getContributors());
		System.out.println(AppInfo.getWebsite());
		System.out.println();
		System.out.println("JWGL Version: " + Version.getVersion());
		System.out.println();

		Utils.forceJarCurrentDirectory();
		
		Utils.deleteAllTmpFiles(Constants.BASE_PATH + "tmp");
		
		AppSettings.getInstance().loadFromFile(Constants.BASE_PATH + Constants.APP_SETTINGS_FILE);
		LatticeManager.getInstance().initialize();

        GLWindow glWindow = new GLWindow(Constants.TITLE);
		Engine engine = new Simulator(glWindow, updateLimit);
		engine.setFrameLimit(frameLimit);

		try {
			engine.init();
			if (openGeometryDiscovery)
				GeometryDiscoveryWorkspaceManager.getInstance().openWorkspace();
			if (startupProjectPath != null) {
				System.out.println("Loading startup project: " + startupProjectPath);
				Project.getInstance().loadFromFile(startupProjectPath.toString());
			}
			engine.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
