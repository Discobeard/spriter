package com.discobeard.spriter;

import com.discobeard.spriter.dom.Animation;
import com.discobeard.spriter.dom.SpriterData;
import com.discobeard.spriter.draw.AbstractDrawer;
import com.discobeard.spriter.draw.DrawInstruction;
import com.discobeard.spriter.file.AbstractLoader;
import com.discobeard.spriter.file.Reference;
import com.discobeard.spriter.objects.SpriterAnimation;
import com.discobeard.spriter.objects.SpriterBone;
import com.discobeard.spriter.objects.SpriterKeyFrame;
import com.discobeard.spriter.objects.SpriterModObject;
import com.discobeard.spriter.objects.SpriterObject;

import java.util.ArrayList;
import java.util.List;

public class SpriterPlayer{

	private SpriterData spriterData;
	private Animation animation;
	private long frame = 0;
	private int frameSpeed = 30, transitionSpeed = 30;
	private int animationIndex = 0;
	private int currentKey = 0;
	private DrawInstruction[] instructions;
	private List<SpriterKeyFrame[]> keyframes;
	private SpriterBone[] tempBones;
	private int currenObjectsToDraw;
	private int flipX = 1, flipY = 1;
	private float angle = 0;
	private float scale = 1f;
	private SpriterBone rootParent;
	private SpriterModObject[] moddedObjects,moddedBones;
	private SpriterKeyFrame lastFrame;
	private boolean transitionFixed = true;
	private int fixCounter = 0;
	private int fixMaxSteps = 100;
	
	
	ArrayList<SpriterAnimation> animations = new ArrayList<>();
	
	@SuppressWarnings("unused")
	private AbstractLoader<?> loader;
	private AbstractDrawer<?> drawer;
	
	public SpriterPlayer(final AbstractLoader<?> loader, final AbstractDrawer<?> drawer){
		this.loader = loader;
		this.drawer = drawer;
	}
	
	public void addAnimation(SpriterAnimation animation){
		animations.add(animation);
	}
	
	/**
	 * Constructs a new SpriterPlayer object which animates the given SpriterData.
	 * @param spriterData
	 * @param drawer
	 * @param keyframes A list of SpriterKeyFrame arrays. See {@link #SpriterKeyFrameProvider.generateKeyFramePool(SpriterData)} to get the list.
	 */
	public SpriterPlayer(SpriterData spriterData, AbstractDrawer<?> drawer,List<SpriterKeyFrame[]> keyframes){
		this.spriterData = spriterData;
		this.keyframes = keyframes;
		this.drawer = drawer;
		this.generatePool();
		this.animation = this.spriterData.getEntity().get(0).getAnimation().get(0);
		this.rootParent = new SpriterBone();
		this.rootParent.setScaleX(this.scale);
		this.rootParent.setScaleY(this.scale);
		this.lastFrame = new SpriterKeyFrame();
		
		SpriterBone[] tmpBones = new SpriterBone[this.tempBones.length];
		SpriterObject[] tmpObjs = new SpriterObject[this.instructions.length];
		for(int i = 0; i < tmpObjs.length; i++)	tmpObjs[i] = new SpriterObject();
		for(int i = 0; i < tmpBones.length; i++) tmpBones[i] = new SpriterBone();
		this.lastFrame.setBones(tmpBones);
		this.lastFrame.setObjects(tmpObjs);
		
	}
	
	private void generatePool(){
		this.instructions = new DrawInstruction[SpriterKeyFrameProvider.MAX_OBJECTS];
		this.moddedObjects = new SpriterModObject[this.instructions.length];
		for(int i = 0; i < this.instructions.length; i++){
			this.instructions[i] = new DrawInstruction(new Reference(0,0),0,0,0,0,0,0,0,0);
			this.moddedObjects[i] = new SpriterModObject();
		}
		this.tempBones = new SpriterBone[SpriterKeyFrameProvider.MAX_BONES];
		this.moddedBones = new SpriterModObject[this.tempBones.length];
		for(int i = 0; i < this.tempBones.length; i++){
			this.tempBones[i] = new SpriterBone();
			this.moddedBones[i] = new SpriterModObject();
		}
	}
	
	/**
	 * Draws the current animation
	 */
	public void draw(){
		for(int i = 0; i< this.currenObjectsToDraw; i++){
			DrawInstruction dI = this.instructions[i];
			this.drawer.draw(dI);
		}
	}
	
	/**
	 * Updates this player and translates the animation to xOffset and yOffset.
	 * @param xOffset
	 * @param yOffset
	 */
	public void update(float xOffset, float yOffset){
		//Fetch information
		SpriterKeyFrame[] keyframes = this.keyframes.get(animationIndex);
		SpriterKeyFrame firstKeyFrame; 
		SpriterKeyFrame secondKeyFrame;
		if(this.transitionFixed){
			firstKeyFrame = keyframes[this.currentKey];
			secondKeyFrame = keyframes[(this.currentKey+1)%keyframes.length];
			
			//Update
			if(this.frame > this.animation.getLength())
				this.frame = 0;
			this.frame += this.frameSpeed;
			if (this.frame > keyframes[this.currentKey].getEndTime()){
				this.currentKey = (this.currentKey+1)%keyframes.length;
				this.frame = keyframes[this.currentKey].getStartTime();
			}
		}
		else{
			firstKeyFrame = keyframes[0];
			secondKeyFrame = this.lastFrame;
			float temp =(float)(this.fixCounter)/(float)this.fixMaxSteps;
			this.frame = this.lastFrame.getStartTime()+(long)(this.fixMaxSteps*temp);
			this.fixCounter= Math.min(this.fixCounter+this.transitionSpeed,this.fixMaxSteps);
			//Update
			if(this.fixCounter == this.fixMaxSteps){
				this.frame = 0;
				this.fixCounter = 0;
				this.transitionFixed = true;
				firstKeyFrame.setStartTime(0);
			}
		}
		this.currenObjectsToDraw = firstKeyFrame.getObjects().length;
		//Interpolate
		this.interpolateBones(firstKeyFrame, secondKeyFrame, xOffset, yOffset);		
		this.interpolateObjects(firstKeyFrame, secondKeyFrame, xOffset, yOffset);
	}
	
	/**
	 * Interpolates the objects of firstFrame and secondFrame.
	 * @param firstFrame
	 * @param secondFrame
	 * @param xOffset
	 * @param yOffset
	 */
	private void interpolateObjects(SpriterKeyFrame firstFrame, SpriterKeyFrame secondFrame, float xOffset, float yOffset) {
		float[] newstuff;
		for (int i = 0; i < this.currenObjectsToDraw; i++) {

			SpriterObject obj1 = firstFrame.getObjects()[i];
			DrawInstruction dI = this.instructions[i];

			if (!obj1.isTransientObject()) {
				SpriterObject obj2 = null;
				boolean found = false;
				for(int j = 0; j < secondFrame.getObjects().length && !found; j++){
					found = secondFrame.getObjects()[j].getTimeline() == obj1.getTimeline();
					if(found) obj2 = secondFrame.getObjects()[j];
				}
				float x=obj1.getX(),y=obj1.getY(),scaleX=obj1.getScaleX(),scaleY=obj1.getScaleY(),rotation=obj1.getAngle(),alpha=obj1.getAlpha();
				if(obj2 != null){
					x = SpriterCalculator.calculateInterpolation(obj1.getX(), obj2.getX(), firstFrame.getStartTime(), secondFrame.getStartTime(), this.frame);
					y = SpriterCalculator.calculateInterpolation(obj1.getY(), obj2.getY(), firstFrame.getStartTime(), secondFrame.getStartTime(), this.frame);
					scaleX = SpriterCalculator.calculateInterpolation(obj1.getScaleX(), obj2.getScaleX(), firstFrame.getStartTime(), secondFrame.getStartTime(), this.frame);
					scaleY = SpriterCalculator.calculateInterpolation(obj1.getScaleY(), obj2.getScaleY(), firstFrame.getStartTime(), secondFrame.getStartTime(), this.frame);
					rotation = SpriterCalculator.calculateAngleInterpolation(obj1.getAngle(), obj2.getAngle(), firstFrame.getStartTime(), secondFrame.getStartTime(), this.frame);
					alpha = SpriterCalculator.calculateInterpolation(obj1.getAlpha(), obj2.getAlpha(), firstFrame.getStartTime(), secondFrame.getStartTime(), this.frame);
				}
				if(this.transitionFixed){
					this.lastFrame.getObjects()[i].setX(x);
					this.lastFrame.getObjects()[i].setY(y);
					this.lastFrame.getObjects()[i].setScaleX(scaleX);
					this.lastFrame.getObjects()[i].setScaleY(scaleY);
					this.lastFrame.getObjects()[i].setAngle(rotation);
					this.lastFrame.getObjects()[i].setAlpha(alpha);
					this.lastFrame.getObjects()[i].setId(obj1.getId());
					this.lastFrame.getObjects()[i].setTimeline((found) ? obj1.getTimeline() : -1);
					this.lastFrame.getObjects()[i].setFile(obj1.getFile());
					this.lastFrame.getObjects()[i].setFolder(obj1.getFolder());
					this.lastFrame.getObjects()[i].setPivotX(obj1.getPivotX());
					this.lastFrame.getObjects()[i].setPivotY(obj1.getPivotY());
					this.lastFrame.getObjects()[i].setSpin(obj1.getSpin());
				}
				
				if (obj1.getParent() != null) {
					rotation += tempBones[obj1.getParent()].getAngle();
					scaleX *= tempBones[obj1.getParent()].getScaleX();
					scaleY *= tempBones[obj1.getParent()].getScaleY();
					newstuff = SpriterCalculator.rotatePoint(tempBones[obj1.getParent()], x, y);
					x = newstuff[0];
					y = newstuff[1];
				}
				else{
					rotation += this.angle;
					scaleX *= this.scale;
					scaleY *= this.scale;
					newstuff = SpriterCalculator.rotatePoint(this.rootParent, x, y);
					x = newstuff[0];
					y = newstuff[1];
				}
				
				dI.ref.folder = obj1.getFolder();
				dI.ref.file = obj1.getFile();
				dI.x =  x*this.flipX+xOffset;
				dI.y =  y*this.flipY+yOffset;
				dI.scaleX =  scaleX*this.flipX;
				dI.scaleY =  scaleY*this.flipY;
				dI.pivotX =  obj1.getPivotX();
				dI.pivotY = obj1.getPivotY();
				dI.angle = rotation*this.flipX*this.flipY;
				dI.alpha = alpha;
					
			}
			else
			{
				dI.ref.folder = obj1.getFolder();
				dI.ref.file = obj1.getFile();
				dI.pivotX = obj1.getPivotX();
				dI.pivotY = obj1.getPivotY();
				dI.scaleX = obj1.getScaleX()*this.scale;
				dI.scaleY = obj1.getScaleY()*this.scale;
				dI.angle = obj1.getAngle()+this.angle;
				dI.alpha = obj1.getAlpha();
				newstuff = SpriterCalculator.rotatePoint(this.rootParent, dI.x, dI.y);
				dI.x = newstuff[0];
				dI.y = newstuff[1];
				dI.x = dI.x*this.flipX+xOffset;
				dI.y = dI.y*this.flipY+yOffset;
			}
		}
	}
	
	/**
	 * Interpolates the bones for this animation.
	 * @param firstFrame first keyframe
	 * @param secondFrame second keyframe
	 * @param currentAnimationTime
	 * @param key2StartTime
	 * @return interpolated SpriterBone array
	 */
	private void interpolateBones(SpriterKeyFrame firstFrame, SpriterKeyFrame secondFrame, float xOffset, float yOffset){
		for (int i = 0; i < firstFrame.getBones().length; i++) {
			SpriterBone bone1 = firstFrame.getBones()[i];
			this.tempBones[i].setName(bone1.getName());
			this.moddedBones[i].setName(bone1.getName());
			SpriterBone bone2 = null;
			boolean found = false;
			for(int j = 0; j < secondFrame.getBones().length && !found; j++){//Get the right bone to interpolate with
				found = secondFrame.getBones()[j].getTimeline() == bone1.getTimeline();
				if(found) bone2 = secondFrame.getBones()[j];
			}
			float x=bone1.getX(),y=bone1.getY(),scaleX=bone1.getScaleX(),scaleY=bone1.getScaleY(),rotation=bone1.getAngle();
			if(bone2 != null){
				x = SpriterCalculator.calculateInterpolation(bone1.getX(), bone2.getX(), firstFrame.getStartTime(), secondFrame.getStartTime(), this.frame);
				y = SpriterCalculator.calculateInterpolation(bone1.getY(), bone2.getY(), firstFrame.getStartTime(), secondFrame.getStartTime(), this.frame);
				scaleX = SpriterCalculator.calculateInterpolation(bone1.getScaleX(), bone2.getScaleX(), firstFrame.getStartTime(), secondFrame.getStartTime(), this.frame);
				scaleY = SpriterCalculator.calculateInterpolation(bone1.getScaleY(), bone2.getScaleY(), firstFrame.getStartTime(), secondFrame.getStartTime(), this.frame);
				rotation = SpriterCalculator.calculateAngleInterpolation(bone1.getAngle(), bone2.getAngle(), firstFrame.getStartTime(), secondFrame.getStartTime(), this.frame);
			}
			rotation += this.moddedBones[i].getAngle();
			scaleX *= this.moddedBones[i].getScaleX();
			scaleY *= this.moddedBones[i].getScaleY();
			this.tempBones[i].setAngle(rotation);
			this.tempBones[i].setScaleX(scaleX);
			this.tempBones[i].setScaleY(scaleY);
			this.tempBones[i].setX(x); 
			this.tempBones[i].setY(y);
			this.tempBones[i].setId(bone1.getId());
			this.tempBones[i].setTimeline(bone1.getTimeline());
			this.tempBones[i].setParent(bone1.getParent()); 
			this.tempBones[i].setName(bone1.getName());
			this.tempBones[i].setSpin(bone1.getSpin());
			if(this.transitionFixed){
				this.tempBones[i].copyValuesTo(this.lastFrame.getBones()[i]);
				if(!found) this.tempBones[i].setTimeline(-1);
			}
			if (this.tempBones[i].getParent() != null) {
				this.tempBones[i].setAngle(this.tempBones[i].getAngle() + tempBones[this.tempBones[i].getParent()].getAngle());
				this.tempBones[i].setScaleX(this.tempBones[i].getScaleX() * tempBones[this.tempBones[i].getParent()].getScaleX());
				this.tempBones[i].setScaleY(this.tempBones[i].getScaleY() * tempBones[this.tempBones[i].getParent()].getScaleY());
				float[] newstuff = SpriterCalculator.rotatePoint(tempBones[this.tempBones[i].getParent()], this.tempBones[i].getX(), this.tempBones[i].getY());
				this.tempBones[i].setX(newstuff[0]);
				this.tempBones[i].setY(newstuff[1]);
			}
			else{
				this.tempBones[i].setAngle(this.tempBones[i].getAngle() + this.rootParent.getAngle());
				this.tempBones[i].setScaleX(this.tempBones[i].getScaleX() * this.rootParent.getScaleX());
				this.tempBones[i].setScaleY(this.tempBones[i].getScaleY() * this.rootParent.getScaleY());
				float[] newstuff = SpriterCalculator.rotatePoint(this.rootParent, this.tempBones[i].getX(), this.tempBones[i].getY());
				this.tempBones[i].setX(newstuff[0]);
				this.tempBones[i].setY(newstuff[1]);
			}
			this.moddedBones[i].setX(this.tempBones[i].getX()+xOffset);
			this.moddedBones[i].setY(this.tempBones[i].getY()+yOffset);
		}
	} 
	
	/**
	 * Switches the current animation to the given one, with smooth transition if required.
	 * @param animationIndex
	 * @param transitionSpeed indicates how fast the animations have to switch
	 * @param transitionSteps indicates how many steps are required to switch between the animations.
	 */
	public void setAnimatioIndex(int animationIndex, int transitionSpeed, int transitionSteps){
		if(this.animationIndex != animationIndex){
			this.transitionSpeed = transitionSpeed;
			this.fixMaxSteps = transitionSteps;
			this.lastFrame.setStartTime(this.frame+1);
			this.lastFrame.setEndTime(this.frame+this.fixMaxSteps-1);
			this.keyframes.get(animationIndex)[0].setStartTime(this.frame+1+this.fixMaxSteps);
			this.transitionFixed = false;
			this.currentKey = 0;
			this.fixCounter = 0;
			this.animationIndex = animationIndex;
			this.animation = this.spriterData.getEntity().get(0).getAnimation().get(animationIndex);
		}
	}
	
	/**
	 * Searches for the animation index with the given name and returns the right one
	 * @param name name of the animation.
	 * @return index of the animation if the given name was found, otherwise it returns -1
	 */
	public int getAnimationIndexByName(String name){
		List<Animation> anims = this.spriterData.getEntity().get(0).getAnimation();
		for(Animation anim: anims)
			if(anim.getName().equals(name)) return anim.getId();
		return -1;
	}
	
	/**
	 * Searches for the bone index with the given name and returns the right one
	 * @param name name of the bone.
	 * @return index of the bone if the given name was found, otherwise it returns -1
	 */
	public int getBoneIndexByName(String name){
		for(int i = 0; i < this.moddedBones.length; i++)
			if(name.equals(this.moddedBones[i].getName())) return i;
		return -1;
	}
	
	/**
	 * Modifies the bone's angle with the given bone index
	 * @param index index of the bone
	 * @param angle new angle of the given bone, angle = 0 means no moddification
	 */
	public void setBoneAngle(int index, float angle){
		this.moddedBones[index].setAngle(angle);
	}
	
	/**
	 * Modifies the bone's scale x with the given bone index
	 * @param index index of the bone
	 * @param scaleX new scale of the given bone, scaleX = 1 means no moddification
	 */
	public void setBoneScaleX(int index, float scaleX){
		this.moddedBones[index].setScaleX(scaleX);
	}
	
	/**
	 * Modifies the bone's scale y with the given bone index
	 * @param index index of the bone
	 * @param scaleY new scale of the given bone, scaleY = 1 means no moddification
	 */
	public void setBoneScaleY(int index, float scaleY){
		this.moddedBones[index].setScaleX(scaleY);
	}
	
	public float getBoneAngle(int index){
		return this.moddedBones[index].getAngle();
	}
	
	public float getBoneScaleX(int index){
		return this.moddedBones[index].getScaleX();
	}
	
	public float getBoneScaleY(int index){
		return this.moddedBones[index].getScaleY();
	}
	
	public float getBoneX(int index){
		return this.moddedBones[index].getX();
	}
	
	public float getBoneY(int index){
		return this.moddedBones[index].getY();
	}
	
	/**
	 * @return current animation index
	 */
	public int getAnimationIndex(){
		return this.animationIndex;
	}
	
	
	/**
	 * Returns the current DrawInstruction array
	 * @param animationIndex
	 * @return
	 */
	public DrawInstruction[] getDrawInstructions(){
		return this.instructions;
	}

	/**
	 * @return the spriterData
	 */
	public SpriterData getSpriterData() {
		return spriterData;
	}

	/**
	 * @param spriterData the spriterData to set
	 */
	public void setSpriterData(SpriterData spriterData) {
		this.spriterData = spriterData;
	}

	/**
	 * Changes the current frame to the given one. Note: You can't change the frame while this object is switching to another animation.
	 * @param frame the frame to set
	 */
	public void setFrame(long frame) {
		this.frame = frame;
	}

	/**
	 * @return the frame
	 */
	public long getFrame() {
		return frame;
	}

	/**
	 * @param frameSpeed the frameSpeed to set
	 */
	public void setFrameSpeed(int frameSpeed) {
		this.frameSpeed = frameSpeed;
	}
	/**
	 * @return the frameSpeed
	 */
	public int getFrameSpeed() {
		return frameSpeed;
	}

	
	/**
	 * @return the anim
	 */
	public Animation getAnimation() {
		return animation;
	}
	
	/**
	 * Flips this around the x-axis.
	 */
	public void flipX(){
		this.flipX *=-1;
	}
	
	/**
	 * @return indicates whether this is flipped around the x-axis or not. 1 means is not flipped.
	 */
	public int getFlipX(){
		return this.flipX;
	}

	
	/**
	 * Flips this around the y-axis.
	 */
	public void flipY(){
		this.flipY *=-1;
	}

	
	/**
	 * @return indicates whether this is flipped around the y-axis or not. 1 means is not flipped.
	 */
	public float getFlipY() {
		return this.flipY;
	}
	
	/**
	 * Changes the angle of this.
	 * @param angle to rotate all objects , angle = 0 means no rotation
	 */
	public void setAngle(float angle){
		this.rootParent.setAngle(this.angle);
		this.angle = angle;
	}
	
	public float getAngle(){
		return this.angle;
	}

	/**
	 * @return the scaleX
	 */
	public float getScale() {
		return scale;
	}

	/**
	 * Scales this to the given value.
	 * @param scale the scale to set, scale = 1.0f normal scale
	 */
	public void setScale(float scale) {
		this.scale = scale;
		this.rootParent.setScaleX(this.scale);
		this.rootParent.setScaleY(this.scale);
	}
	
	/**
	 * Sets the center point of this. pivotX = 0, pivotY = 0 means the same rotation point as in Spriter.
	 * @param pivotX
	 * @param pivotY
	 */
	public void setPivot(float pivotX, float pivotY){
		this.rootParent.setX(pivotX);
		this.rootParent.setY(pivotY);
	}
	
	public float getPivotX(){
		return this.rootParent.getX();
	}
	
	public float getPivotY(){
		return this.rootParent.getY();
	}
}
