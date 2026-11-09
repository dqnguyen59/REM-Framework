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
package remframework.opengl.view.renderer;

import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;
import remframework.helpers.Constants;
import remframework.opengl.model.Camera;
import remframework.opengl.model.ObjFileModel;
import remframework.opengl.model.Scene;
import remframework.opengl.model.entity.Entity;
import remframework.opengl.model.lights.DirectionalLight;
import remframework.opengl.model.lights.Light;
import remframework.settings.Project;
import remframework.settings.Settings;
import remframework.helpers.Utils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class EntityRenderer implements IRenderer {

	Project project = Project.getInstance();
	
	Shader shader;
    private Shader surveyLineShader;
    private int surveyLineVao,surveyLineVbo;
    private Shader connectionShader;
    private int connectionVao,connectionVbo;
    private FloatBuffer connectionVertices;
    private boolean slabContextPass;
    @Getter
    private Map<ObjFileModel, List<Entity>> entities = new  HashMap<>();

	private int numLights = Constants.INITIAL_NUM_LIGHTS;
	private String vertexShader;
	private String fragmentShader;
	private final Map<ObjFileModel, Integer> instanceBuffers = new HashMap<>();
	private final Map<ObjFileModel, List<Entity>> visibleEntityCache = new HashMap<>();
	private boolean visibleEntityCacheDirty = true;
	private boolean cachedInspectionMode;
	private long cachedInspectionVisibilityVersion = Long.MIN_VALUE;
	private static final int INSTANCE_FLOATS = 30;
	private static final int INSTANCE_STRIDE_BYTES = INSTANCE_FLOATS * Float.BYTES;
	private static final int INSTANCE_THRESHOLD = 32;
	private long instanceRenderWindowStartedNanos;
	private long instanceRenderWindowNanos;
	private long instanceRenderWindowCalls;
	private long instanceRenderWindowInstances;
	private long instanceRenderLastNanos;
	private long instanceBufferUploadWindowNanos;
	private long instanceBufferUploadWindowCalls;
	private long instanceBufferUploadLastNanos;
	private long instanceBufferUploadMaxNanos;
	private long instancePreparationWindowNanos;
	private long instancePreparationLastNanos;
	private long instancePreparationMaxNanos;
	private long instanceDrawWindowNanos;
	private long instanceDrawLastNanos;
	private long instanceDrawMaxNanos;
	private long renderWindowStartedNanos;
	private long renderWindowNanos;
	private long renderWindowCalls;
	private long renderLastNanos;
	
	public EntityRenderer() throws Exception {
		entities = new HashMap<>();
		shader = new Shader();
	}
	
	@Override
	public void init() throws Exception {
		vertexShader = Utils.loadResource(Constants.SHADER_NODE_VERTEX_FILE);
		fragmentShader = Utils.loadResource(Constants.SHADER_NODE_FRAGMENT_FILE);
		updateShaders();
        surveyLineShader=new Shader();
        surveyLineShader.createVertextShader("#version 410 core\nlayout(location=0) in vec3 position; uniform mat4 mvp; void main(){gl_Position=mvp*vec4(position,1.0);}");
        surveyLineShader.createFragmentShader("#version 410 core\nuniform float opacity; out vec4 color; void main(){color=vec4(1.0,0.1,0.8,opacity);}");
        surveyLineShader.link();surveyLineShader.createUniform("mvp");surveyLineShader.createUniform("opacity");
        surveyLineVao=GL30.glGenVertexArrays();surveyLineVbo=GL15.glGenBuffers();
        connectionShader=new Shader();
        connectionShader.createVertextShader("#version 410 core\nlayout(location=0) in vec3 position; layout(location=1) in vec4 nodeColor; uniform mat4 mvp; out vec4 endpointColor; void main(){gl_Position=mvp*vec4(position,1.0);endpointColor=nodeColor;}");
        connectionShader.createFragmentShader("#version 410 core\nin vec4 endpointColor; out vec4 color; void main(){color=endpointColor;}");
        connectionShader.link();connectionShader.createUniform("mvp");
        connectionVao=GL30.glGenVertexArrays();connectionVbo=GL15.glGenBuffers();
	}

	public void updateShaders() {
		try {
			shader.createVertextShader(vertexShader);
			String fragmentShader = this.fragmentShader.replace("%MAX_LIGHTS%", "" + numLights);
			shader.createFragmentShader(fragmentShader);
			shader.link();
			shader.createUniform("textureSampler");
			shader.createUniform("transformationMatrix");
			shader.createUniform("projectionMatrix");
			shader.createUniform("viewMatrix");
			shader.createUniform("ambientLight");
			shader.createMaterialUniform("material");
			shader.createUniform("specularPower");
			shader.createUniform("depthVisibility");
			shader.createUniform("ignoreLightIndex");
			shader.createUniform("useInstanceMaterial");
            shader.createUniform("slabPass");
			shader.createDirectionalLightUniform("directionalLight");
			shader.createLightListUniform("lights", numLights);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void renderLights(DirectionalLight directionalLight, List<Light> lights, Shader shader) {
		if (numLights != lights.size()) {
			numLights = lights.size();
			updateShaders();
			try {
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		shader.setUniform("ambientLight", Constants.AMBIENT_LIGHT);
		shader.setUniform("specularPower", Constants.SPECULAR_POWER);
		shader.setUniform("directionalLight", directionalLight);
		
		for (int i = 0; i < numLights; i++) {
			shader.setUniform("lights", lights.get(i), i);
			shader.setUniform("ignoreLightIndex", i);
			renderLight(shader, lights.get(i));
		}
	}

	public void renderLight(Shader shader, Light light) {
		if (light.getIntensity() > 0) {
			light.updateMatrix();
			ObjFileModel model = light.getModel();
			bind(model);
			shader.setUniform("material", light.getMaterial());
			shader.setUniform("transformationMatrix", light.getTransformMatrix4f());
			GL11.glDrawElements(GL11.GL_TRIANGLES, model.getVertexCount(), GL11.GL_UNSIGNED_INT, 0);

			if (light.isSpotLight()) {
				model = light.getModelSpotLight();
				bind(model);
				shader.setUniform("material", light.getSpotLightHolder().getMaterial());
				shader.setUniform("transformationMatrix", light.getSpotLightHolder().getTransformMatrix4f());
				GL11.glDrawElements(GL11.GL_TRIANGLES, model.getVertexCount(), GL11.GL_UNSIGNED_INT, 0);
			}
		}
	}
	
	@Override
	public void render(Camera camera, Scene scene) {
		render(camera, scene, List.of(), List.of());
	}

	/** Renders the static lattice batch followed by small, independently packed overlay batches. */
	public void render(Camera camera, Scene scene, List<Entity> probeEntities,
			List<Entity> gradientArrowEntities) {
		long startedNanos = System.nanoTime();
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		shader.bind();
		shader.setUniform("textureSampler", 0);
		shader.setUniform("projectionMatrix", camera.getProjectionMatrix());
		shader.setUniform("viewMatrix", camera.getViewMatrix());
		// Inspector geometry is diagnostic and must not fade as the camera moves through
		// the normal depth-visibility field. The shader and ordinary render path remain unchanged.
		shader.setUniform("depthVisibility", scene.isInspectionMode()
				? 0.0f : project.settings.getDepthVisibilityFactor());

		renderLights(scene.getDirectionalLight(), project.getLights(), shader);
		shader.setUniform("useInstanceMaterial", 0);
		boolean enhancedRendering = project.settings.getRenderingMethod()
				!= Settings.RenderingMethod.DEFAULT_RENDERING;
        boolean context=project.settings.isPhysicalSlab()&&project.settings.isShowOutsideSlab()&&project.settings.getOutsideSlabOpacity()>0&&!scene.isInspectionMode();
        slabContextPass=false;shader.setUniform("slabPass",context?1:0);
		if (!enhancedRendering) {
			renderDefault(scene);
		} else {
			renderEnhanced(scene);
		}
        if(context) {
            boolean writes=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);GL11.glDepthMask(false);
            slabContextPass=true;shader.setUniform("slabPass",2);
            try{if(enhancedRendering)renderEnhanced(scene);else renderDefault(scene);}
            finally{GL11.glDepthMask(writes);slabContextPass=false;}
        }
        shader.setUniform("slabPass",0);
		renderOverlays(scene, probeEntities, gradientArrowEntities, enhancedRendering);

		unbind();
		shader.unbind();
        renderConnections(camera,scene);
        renderSurveyLines(camera,scene);
		GL11.glDisable(GL11.GL_BLEND);
		recordRenderMeasurement(startedNanos);
	}

	/** Keeps dynamic probe geometry out of the prepared lattice-node instance buffer. */
	private void renderOverlays(Scene scene, List<Entity> probeEntities,
			List<Entity> gradientArrowEntities, boolean enhancedRendering) {
		renderOverlayBatch(scene,probeEntities,gradientArrowEntities,enhancedRendering);
		if(!scene.getSurveyLineOverlays().isEmpty()) {
            boolean writes=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);GL11.glDepthMask(false);
			try {
                var marks=scene.getSurveyLineOverlays();
                for(var marker:marks){marker.updateMatrix();marker.synchronizeTransformMatrixBuffers();}
                renderOverlayBatch(scene,marks,List.of(),enhancedRendering);
            }
			finally {GL11.glDepthMask(writes);}
		}
	}

    /** Streams each visible undirected bond once, using bounded reusable storage. */
    private void renderConnections(Camera camera,Scene scene) {
        var display=scene.getAppSettings();
        if(!display.isShowLatticeConnections()||display.getLatticeConnectionOpacity()<=0||scene.isInspectionMode())return;
        var nodes=scene.getLattice().getNodeArray();if(nodes==null||nodes.length==0)return;
        if(connectionVertices==null)connectionVertices=MemoryUtil.memAllocFloat(16384*14);
        boolean writes=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        float width=GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        GL11.glDepthMask(false);GL11.glLineWidth(1);
        try {
            connectionShader.bind();
            connectionShader.setUniform("mvp",new org.joml.Matrix4f(camera.getProjectionMatrix()).mul(camera.getViewMatrix()));
            GL30.glBindVertexArray(connectionVao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,connectionVbo);
            GL20.glEnableVertexAttribArray(0);GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,7*Float.BYTES,0);
            GL20.glEnableVertexAttribArray(1);GL20.glVertexAttribPointer(1,4,GL11.GL_FLOAT,false,7*Float.BYTES,3L*Float.BYTES);
            connectionVertices.clear();
            for(Object value:nodes)if(value instanceof remframework.opengl.model.entity.Node node&&node.isVisible()) {
                for(var neighbor:node.getNeighbors()) {
                    if(neighbor==null||node.getRenderEntityIndex()>=neighbor.getRenderEntityIndex())continue;
                    if(connectionVertices.remaining()<14)flushConnections();
                    int start=connectionVertices.position();
                    if(!scene.appendConnectionVertex(node,connectionVertices,display.getLatticeConnectionOpacity())
                            ||!scene.appendConnectionVertex(neighbor,connectionVertices,display.getLatticeConnectionOpacity()))
                        connectionVertices.position(start);
                }
            }
            flushConnections();
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,0);GL30.glBindVertexArray(0);connectionShader.unbind();
            GL11.glDepthMask(writes);GL11.glLineWidth(width);
        }
    }

    private void flushConnections() {
        if(connectionVertices.position()==0)return;
        connectionVertices.flip();GL15.glBufferData(GL15.GL_ARRAY_BUFFER,connectionVertices,GL15.GL_STREAM_DRAW);
        GL11.glDrawArrays(GL11.GL_LINES,0,connectionVertices.remaining()/7);connectionVertices.clear();
    }

    /** Real GL_LINES in lattice coordinates, independent of sphere geometry and lighting. */
    private void renderSurveyLines(Camera camera,Scene scene) {
        var segments=scene.getSurveySegments();if(segments.isEmpty())return;
        float[] vertices=new float[segments.size()*6];int offset=0;
        var root=scene.getLattice().getBaseEntity().getTransformMatrix4f();
        for(var segment:segments)for(boolean start:new boolean[]{true,false}) {
            var node=start?segment.from():segment.to();
            org.joml.Vector3f point;
            if(node!=null)point=node.getTransformMatrix4f().getTranslation(new org.joml.Vector3f());
            else point=root.transformPosition(new org.joml.Vector3f(start?segment.start():segment.end()));
            vertices[offset++]=point.x;vertices[offset++]=point.y;vertices[offset++]=point.z;
        }
        float width=GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        GL11.glLineWidth(1);
        try {
            surveyLineShader.bind();
            var mvp=new org.joml.Matrix4f(camera.getProjectionMatrix()).mul(camera.getViewMatrix());
            surveyLineShader.setUniform("mvp",mvp);
            surveyLineShader.setUniform("opacity",(float)scene.getAppSettings().getSurveyLineOpacity());
            GL30.glBindVertexArray(surveyLineVao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,surveyLineVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,vertices,GL15.GL_STREAM_DRAW);
            GL20.glEnableVertexAttribArray(0);GL20.glVertexAttribPointer(0,3,GL11.GL_FLOAT,false,0,0);
            GL11.glDrawArrays(GL11.GL_LINES,0,segments.size()*2);
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,0);GL30.glBindVertexArray(0);surveyLineShader.unbind();
            GL11.glLineWidth(width);
        }
    }

	private void renderOverlayBatch(Scene scene,List<Entity> probeEntities,
			List<Entity> gradientArrowEntities,boolean enhancedRendering) {
		Map<ObjFileModel, List<Entity>> overlaysByModel = new IdentityHashMap<>();
		collectVisibleOverlays(scene, probeEntities, overlaysByModel);
		collectVisibleOverlays(scene, gradientArrowEntities, overlaysByModel);
		for (Map.Entry<ObjFileModel, List<Entity>> entry : overlaysByModel.entrySet()) {
			ObjFileModel model = entry.getKey();
			List<Entity> visibleEntities = entry.getValue();
			if (model == null || visibleEntities.isEmpty()) continue;
			if (enhancedRendering && canInstance(visibleEntities)) {
				renderInstanced(model, visibleEntities);
				continue;
			}
			bind(model);
			shader.setUniform("ignoreLightIndex", -1);
			shader.setUniform("useInstanceMaterial", 0);
			for (Entity entity : visibleEntities) {
				shader.setUniform("material", entity.getMaterial());
				shader.setUniform("transformationMatrix", entity.getTransformMatrix4f());
				GL11.glDrawElements(GL11.GL_TRIANGLES, model.getVertexCount(),
						GL11.GL_UNSIGNED_INT, 0);
			}
		}
	}

	private void collectVisibleOverlays(Scene scene, List<Entity> overlays,
			Map<ObjFileModel, List<Entity>> overlaysByModel) {
		for (Entity entity : overlays) {
			boolean effectiveVisible = scene.isInspectionMode()
					? entity.isInspectionVisible() : entity.isVisible();
			if (!effectiveVisible || entity.getModel() == null) continue;
			overlaysByModel.computeIfAbsent(entity.getModel(), ignored -> new ArrayList<>())
					.add(entity);
		}
	}

	/** Invalidates cached visibility after the renderer entity set changes. */
	public void invalidateVisibleEntityCache() {
		visibleEntityCacheDirty = true;
		visibleEntityCache.clear();
	}

	private void recordRenderMeasurement(long startedNanos) {
		long now = System.nanoTime();
		long elapsedNanos = now - startedNanos;
		if (renderWindowStartedNanos == 0L) renderWindowStartedNanos = startedNanos;
		renderLastNanos = elapsedNanos;
		renderWindowNanos += elapsedNanos;
		renderWindowCalls++;
		if (now - renderWindowStartedNanos >= 500_000_000L) {
			double averageMillis = renderWindowNanos
					/ (double) renderWindowCalls / 1_000_000.0;
			System.out.printf("EntityRenderer.render: calls=%d last=%.3f ms average=%.3f ms%n",
					renderWindowCalls, renderLastNanos / 1_000_000.0, averageMillis);
			renderWindowStartedNanos = now;
			renderWindowNanos = 0L;
			renderWindowCalls = 0L;
		}
	}

	private void renderDefault(Scene scene) {
		for (ObjFileModel model : entities.keySet()) {
			if (model == null) continue;
			bind(model);
			shader.setUniform("ignoreLightIndex", -1);
			List<Entity> entityList = entities.get(model);
			for (Entity entity : entityList) {
                if(!scene.isInspectionMode() && ((entity instanceof remframework.opengl.model.entity.Node node&&node.isSlabContextNode())!=slabContextPass))continue;
				boolean effectiveVisible = scene.isInspectionMode()
						? entity.isInspectionVisible() : entity.isVisible();
				if (!effectiveVisible) continue;
				if (entity.isOverrideModelMaterial()) shader.setUniform("material", entity.getMaterial());
				shader.setUniform("transformationMatrix", entity.getTransformMatrix4f());
				GL11.glDrawElements(GL11.GL_TRIANGLES, model.getVertexCount(), GL11.GL_UNSIGNED_INT, 0);
			}
		}
	}

	private void renderEnhanced(Scene scene) {
		ensureVisibleEntityCache(scene);

		for (ObjFileModel model : entities.keySet()) {
			if (model != null) {
				bind(model);
				shader.setUniform("ignoreLightIndex", -1);
				List<Entity> visibleEntities = visibleEntityCache.getOrDefault(model, List.of());
				if (canInstance(visibleEntities)) {
					renderInstanced(model, visibleEntities, scene);
					continue;
				}
				shader.setUniform("useInstanceMaterial", 0);
				for (Entity entity : visibleEntities) {
                    if(!scene.isInspectionMode() && ((entity instanceof remframework.opengl.model.entity.Node node&&node.isSlabContextNode())!=slabContextPass))continue;
					shader.setUniform("material", entity.getMaterial());
					shader.setUniform("transformationMatrix", entity.getTransformMatrix4f());
					GL11.glDrawElements(GL11.GL_TRIANGLES, model.getVertexCount(),
							GL11.GL_UNSIGNED_INT, 0);
				}
			}
		}

	}

	private void ensureVisibleEntityCache(Scene scene) {
		boolean inspectionMode = scene.isInspectionMode();
		long inspectionVersion = scene.getInspectionVisibilityVersion();
		if (!visibleEntityCacheDirty
				&& cachedInspectionMode == inspectionMode
				&& cachedInspectionVisibilityVersion == inspectionVersion) return;

		visibleEntityCache.clear();
		for (Map.Entry<ObjFileModel, List<Entity>> entry : entities.entrySet()) {
			List<Entity> visible = visibleEntities(entry.getValue(), scene);
			visibleEntityCache.put(entry.getKey(), visible);
		}
		cachedInspectionMode = inspectionMode;
		cachedInspectionVisibilityVersion = inspectionVersion;
		visibleEntityCacheDirty = false;
	}

	private List<Entity> visibleEntities(List<Entity> entityList, Scene scene) {
		List<Entity> visible = new java.util.ArrayList<>(entityList.size());
		for (Entity entity : entityList) {
			if (scene.isInspectionMode() ? entity.isInspectionVisible() : entity.isVisible())
				visible.add(entity);
		}
		return visible;
	}

	private boolean canInstance(List<Entity> visibleEntities) {
		if (visibleEntities.size() < INSTANCE_THRESHOLD) return false;
		for (Entity entity : visibleEntities) {
			if (!entity.isOverrideModelMaterial() || entity.getMaterial().hasTexture()) return false;
		}
		return true;
	}

	private void renderInstanced(ObjFileModel model, List<Entity> visibleEntities) {
		renderInstanced(model, visibleEntities, null);
	}

	private void renderInstanced(ObjFileModel model, List<Entity> visibleEntities, Scene scene) {
		long startedNanos = System.nanoTime();
		int bufferId = instanceBuffers.computeIfAbsent(model, this::createInstanceBuffer);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
		long preparationStartedNanos = System.nanoTime();
		FloatBuffer data = preparedInstanceData(scene, model, visibleEntities.size());
		boolean ownsData = data == null;
		int instanceCount = ownsData ? visibleEntities.size() : scene.getRenderInstanceEntityCount(model);
		if (ownsData) data = MemoryUtil.memAllocFloat(visibleEntities.size() * INSTANCE_FLOATS);
		try {
			if (ownsData) {
				for (Entity entity : visibleEntities) {
					entity.getTransformMatrix4f().get(data);
					data.position(data.position() + 16);
					put(data, entity.getMaterial().getAmbientIntensityColor());
					put(data, entity.getMaterial().getDiffuseIntensityColor());
					put(data, entity.getMaterial().getSpecularIntensityColor());
					data.put((float) entity.getMaterial().getShininess());
					data.put(entity instanceof remframework.opengl.model.entity.Node node&&node.isSlabContextNode()?2.0f:1.0f);
				}
				data.flip();
			}
			long preparationElapsedNanos = System.nanoTime() - preparationStartedNanos;
			instancePreparationLastNanos = preparationElapsedNanos;
			instancePreparationWindowNanos += preparationElapsedNanos;
			instancePreparationMaxNanos = Math.max(instancePreparationMaxNanos,
					preparationElapsedNanos);
			long bufferUploadStartedNanos = System.nanoTime();
            if(!slabContextPass||ownsData)GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);
			long bufferUploadElapsedNanos = System.nanoTime() - bufferUploadStartedNanos;
			instanceBufferUploadLastNanos = bufferUploadElapsedNanos;
			instanceBufferUploadWindowNanos += bufferUploadElapsedNanos;
			instanceBufferUploadWindowCalls++;
			instanceBufferUploadMaxNanos = Math.max(instanceBufferUploadMaxNanos,
					bufferUploadElapsedNanos);
		} finally {
			if (ownsData) MemoryUtil.memFree(data);
		}
		shader.setUniform("useInstanceMaterial", 1);
		GL30.glBindVertexArray(model.getId());
		long drawStartedNanos = System.nanoTime();
		GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, model.getVertexCount(),
				GL11.GL_UNSIGNED_INT, 0, instanceCount);
		long drawElapsedNanos = System.nanoTime() - drawStartedNanos;
		instanceDrawLastNanos = drawElapsedNanos;
		instanceDrawWindowNanos += drawElapsedNanos;
		instanceDrawMaxNanos = Math.max(instanceDrawMaxNanos, drawElapsedNanos);
		shader.setUniform("useInstanceMaterial", 0);

		long elapsedNanos = System.nanoTime() - startedNanos;
		if (instanceRenderWindowStartedNanos == 0L)
			instanceRenderWindowStartedNanos = startedNanos;
		instanceRenderLastNanos = elapsedNanos;
		instanceRenderWindowNanos += elapsedNanos;
		instanceRenderWindowCalls++;
		instanceRenderWindowInstances += instanceCount;
		if (System.nanoTime() - instanceRenderWindowStartedNanos >= 500_000_000L) {
			double averageMillis = instanceRenderWindowNanos
					/ (double) instanceRenderWindowCalls / 1_000_000.0;
			double bufferUploadAverageMillis = instanceBufferUploadWindowNanos
					/ (double) instanceBufferUploadWindowCalls / 1_000_000.0;
			System.out.printf("EntityRenderer.renderInstanced: calls=%d instances=%d "
					+ "last=%.3f ms average=%.3f ms; preparation "
					+ "last=%.3f ms average=%.3f ms max=%.3f ms; glBufferData "
					+ "last=%.3f ms average=%.3f ms max=%.3f ms; draw "
					+ "last=%.3f ms average=%.3f ms max=%.3f ms%n",
					instanceRenderWindowCalls, instanceRenderWindowInstances,
					instanceRenderLastNanos / 1_000_000.0, averageMillis,
					instancePreparationLastNanos / 1_000_000.0,
					instancePreparationWindowNanos
							/ (double) instanceRenderWindowCalls / 1_000_000.0,
					instancePreparationMaxNanos / 1_000_000.0,
					instanceBufferUploadLastNanos / 1_000_000.0,
					bufferUploadAverageMillis,
					instanceBufferUploadMaxNanos / 1_000_000.0,
					instanceDrawLastNanos / 1_000_000.0,
					instanceDrawWindowNanos
							/ (double) instanceRenderWindowCalls / 1_000_000.0,
					instanceDrawMaxNanos / 1_000_000.0);
			instanceRenderWindowStartedNanos = System.nanoTime();
			instanceRenderWindowNanos = 0L;
			instanceRenderWindowCalls = 0L;
			instanceRenderWindowInstances = 0L;
			instanceBufferUploadWindowNanos = 0L;
			instanceBufferUploadWindowCalls = 0L;
			instanceBufferUploadMaxNanos = 0L;
			instancePreparationWindowNanos = 0L;
			instancePreparationMaxNanos = 0L;
			instanceDrawWindowNanos = 0L;
			instanceDrawMaxNanos = 0L;
		}
	}

	private FloatBuffer preparedInstanceData(Scene scene, ObjFileModel model,
			int visibleEntityCount) {
		return scene == null || scene.isInspectionMode() || model == null
				|| scene.getRenderInstanceEntityCount(model) != visibleEntityCount
				? null : scene.getRenderInstanceData(model);
	}

	private static void put(FloatBuffer data, org.joml.Vector4f value) {
		data.put(value.x).put(value.y).put(value.z).put(value.w);
	}

	private int createInstanceBuffer(ObjFileModel model) {
		int bufferId = GL15.glGenBuffers();
		GL30.glBindVertexArray(model.getId());
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
		for (int column = 0; column < 4; column++) {
			int location = 3 + column;
			GL20.glEnableVertexAttribArray(location);
			GL20.glVertexAttribPointer(location, 4, GL11.GL_FLOAT, false,
					INSTANCE_STRIDE_BYTES, (long) column * 4 * Float.BYTES);
			GL33.glVertexAttribDivisor(location, 1);
		}
		instanceAttribute(7, 16L * Float.BYTES);
		instanceAttribute(8, 20L * Float.BYTES);
		instanceAttribute(9, 24L * Float.BYTES);
		GL20.glEnableVertexAttribArray(10);
		GL20.glVertexAttribPointer(10, 1, GL11.GL_FLOAT, false, INSTANCE_STRIDE_BYTES,
				28L * Float.BYTES);
		GL33.glVertexAttribDivisor(10, 1);
		GL20.glEnableVertexAttribArray(11);
		GL20.glVertexAttribPointer(11, 1, GL11.GL_FLOAT, false, INSTANCE_STRIDE_BYTES,
				29L * Float.BYTES);
		GL33.glVertexAttribDivisor(11, 1);
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
		GL30.glBindVertexArray(0);
		return bufferId;
	}

	private static void instanceAttribute(int location, long offset) {
		GL20.glEnableVertexAttribArray(location);
		GL20.glVertexAttribPointer(location, 4, GL11.GL_FLOAT, false,
				INSTANCE_STRIDE_BYTES, offset);
		GL33.glVertexAttribDivisor(location, 1);
	}

	@Override
	public void bind(ObjFileModel model) {
		shader.setUniform("material", model.getMaterial());
		GL30.glBindVertexArray(model.getId());
		GL20.glEnableVertexAttribArray(0);
		GL20.glEnableVertexAttribArray(1);
		GL20.glEnableVertexAttribArray(2);

		if (model.getMaterial().isDisableCulling())
			Renderer.disableCulling();
		else
			Renderer.enableCulling();
			
		
		GL20.glActiveTexture(GL13.GL_TEXTURE0);
		if (model.getTexture() != null)
			GL30.glBindTexture(GL11.GL_TEXTURE_2D, model.getTexture().getId());
	}

	@Override
	public void unbind() {
		GL20.glDisableVertexAttribArray(0);
		GL20.glDisableVertexAttribArray(1);
		GL20.glDisableVertexAttribArray(2);
		GL30.glBindVertexArray(0);
	}

	@Override
	public void cleanup() {
        if(connectionShader!=null)connectionShader.cleanup();
        if(connectionVbo!=0)GL15.glDeleteBuffers(connectionVbo);
        if(connectionVao!=0)GL30.glDeleteVertexArrays(connectionVao);
        if(connectionVertices!=null){MemoryUtil.memFree(connectionVertices);connectionVertices=null;}
        if(surveyLineShader!=null)surveyLineShader.cleanup();
        if(surveyLineVbo!=0)GL15.glDeleteBuffers(surveyLineVbo);
        if(surveyLineVao!=0)GL30.glDeleteVertexArrays(surveyLineVao);
		for (int bufferId : instanceBuffers.values()) {
			GL15.glDeleteBuffers(bufferId);
		}
		instanceBuffers.clear();
		shader.cleanup();
	}

}
