package com.theincgi.advancedMacros.gui;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.theincgi.advancedMacros.AdvancedMacros;
import com.theincgi.advancedMacros.gui.elements.Drawable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;

public class Gui extends net.minecraft.client.gui.GuiScreen{
	FontRenderer fontRend = AdvancedMacros.getMinecraft().fontRenderer;
	private LinkedList<KeyTime> heldKeys = new LinkedList<>();
	private LinkedList<InputSubscriber> inputSubscribers = new LinkedList<>();
	/**The next key or mouse event will be sent to this before anything else*/
	public InputSubscriber nextKeyListen = null;
	private LinkedList<Drawable> drawables = new LinkedList<>();
	public volatile Drawable drawLast = null;
	private Object focusItem = null;
	/**Strictly key typed and mouse clicked events atm*/
	public InputSubscriber firstSubsciber;

	private Queue<InputSubscriber> inputSubscriberToAdd = new LinkedList<>(), inputSubscribersToRemove = new LinkedList<>();
	private Queue<Drawable> drawableToAdd = new LinkedList<>(), drawableToRemove = new LinkedList<>();

	private int repeatMod = 0;
	private boolean drawDefaultBackground = true;

	public Gui() {
		super.mc = AdvancedMacros.getMinecraft();
	}

	@Override
	public void drawHorizontalLine(int startX, int endX, int y, int color) {
		super.drawHorizontalLine(startX, endX, y, color);
	}
	@Override
	public void drawVerticalLine(int x, int startY, int endY, int color) {
		super.drawVerticalLine(x, startY, endY, color);
	}
	public static void drawBoxedRectangle(int x,int y, int w, int h,int boarderW,int frame, int fill){
		drawRect(x, 	     y, 	     x+w+1, 		   y+h+1, 		   frame);
		drawRect(x+boarderW, y+boarderW, x+w-boarderW+1,   y+h-boarderW+1, fill);
	}
	public void drawBoxedRectangle(int x,int y, int w, int h,int frame, int fill){
		drawRect(x, y, x+w+1,   y+h+1, fill);
		drawHollowRect(x, y, w, h, frame);
	}
	private void drawHollowRect(int x,int y,int w,int h,int col){
		drawHorizontalLine(x, x+w, y, col);
		drawHorizontalLine(x, x+w, y+h, col);
		drawVerticalLine(x, y, y+h, col);
		drawVerticalLine(x+w, y, y+h, col);
	}

	/**returns next x to use in this for multiColoring*/
	public int drawMonospaceString(String str, int x, int y, int color){
		FontRenderer fr = getFontRend();
		int cWid = (int) ((8f/12) * fr.FONT_HEIGHT);
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			int offset = cWid/2 - fr.getCharWidth(c)/2;
			fr.drawString(c+"", x+offset+cWid*i, y, color);
		}
		return cWid*str.length()+x;
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		onKeyTyped(typedChar, keyCode);
	}
	public boolean onKeyTyped(char typedChar, int keyCode) {
		if(nextKeyListen!=null && nextKeyListen.onKeyPressed(this, typedChar, keyCode)){nextKeyListen = null; return true;}
		if(firstSubsciber!=null && firstSubsciber.onKeyPressed(this, typedChar, keyCode)){return true;}
		try {
			super.keyTyped(typedChar, keyCode);
		} catch (IOException e) {}
		heldKeys.add(new KeyTime(keyCode, typedChar));
		synchronized (inputSubscribers) {
			for (InputSubscriber inputSubscriber : inputSubscribers) {
				if(inputSubscriber.onKeyPressed(this, typedChar, keyCode)) return true;
			}
		}
		return false;
	}
	/**fires after key has been held in for a time
	 * mod will always be positive
	 * <br><b>Tip</b>: Use mod to reduce key repeat speed.
	 * <blockquote><br> if(mod%5==0){...} </code></blockquote>>*/
	public boolean keyRepeated(char typedChar, int keyCode, int mod){
		if(firstSubsciber!=null && firstSubsciber.onKeyRepeat(this, typedChar, keyCode, mod)){return true;}
		synchronized (inputSubscribers) {
			for (InputSubscriber inputSubscriber : inputSubscribers) {
				if(inputSubscriber.onKeyRepeat(this, typedChar, keyCode, mod)) return true;
			}
		}
		return false;
	}

	/**very overridable, this is called after input subscribers have not claimed this event*/
	public boolean onKeyRelease(Gui gui, char typedChar, int keyCode) {
		return false;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		synchronized (inputSubscribers) {
			synchronized (inputSubscriberToAdd) {
				while(!inputSubscriberToAdd.isEmpty()) {
					inputSubscribers.add( inputSubscriberToAdd.poll() );
				}
			}
			synchronized (inputSubscribersToRemove) {
				while(!inputSubscribersToRemove.isEmpty()) {
					inputSubscribers.remove( inputSubscribersToRemove.poll() );
				}
			}
			
		}
		synchronized (drawables) {
			synchronized (drawableToAdd) {
				while(!drawableToAdd.isEmpty()) {
					drawables.add( drawableToAdd.poll() );
				}
			}
			synchronized (drawableToRemove) {
				while(!drawableToRemove.isEmpty()) {
					drawables.remove( drawableToRemove.poll() );
				}
			}
			
		}


		if(drawDefaultBackground)
			drawDefaultBackground();

		if(AdvancedMacros.getMinecraft().currentScreen == this) { //do not steal the child gui's events!
			int i = Mouse.getDWheel();
			if(i!=0)
				mouseScroll((int) Math.signum(i));
			Stack<KeyTime> killList = new Stack<>();
			for(KeyTime k:heldKeys){
				k.fireKeyRepeat();
				if(k.dead){
					killList.push(k);
				}
			}
			for (KeyTime keyTime : killList) {
				boolean flag = false;
				synchronized (inputSubscribers) {
					for (InputSubscriber inputSubscriber : inputSubscribers) {
						if(inputSubscriber.onKeyRelease(this, keyTime.key, keyTime.keyCode)) {
							flag = true;
							break;
						}
					}
				}
				if(!flag)
					onKeyRelease(this, keyTime.key, keyTime.keyCode);
				heldKeys.remove(keyTime);
			}
		}

		synchronized (drawables) {
			for (Drawable drawable : drawables) {
				GlStateManager.pushAttrib();
				//GlStateManager.enableAlpha();
				//GlStateManager.disableBlend();
				//GlStateManager.enableColorMaterial();


				drawable.onDraw(this, mouseX, mouseY, partialTicks);
				GlStateManager.popAttrib();
			}
		}
		if(drawLast!=null){
			drawLast.onDraw(this, mouseX, mouseY, partialTicks);
		}
	}
	//	public static ResourceLocation TEXTURE = new ResourceLocation("textures/gui/container/hopper.png");
	public void drawImage(ResourceLocation texture, int x, int y, int wid, int hei, float uMin, float vMin, float uMax, float vMax){


		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		//GlStateManager.pushMatrix();
		//GlStateManager.pushAttrib();

		AdvancedMacros.getMinecraft().getTextureManager().bindTexture(texture);

		//GlStateManager.enableBlend();
		//GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		//GlStateManager.color(1F, 1F, 1F, 1F);
		GlStateManager.enableAlpha();
		BufferBuilder buffer = Tessellator.getInstance().getBuffer();
		buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
		buffer.pos(x, y, 0).tex(uMin, vMin).endVertex();
		buffer.pos(x, y+hei, 0).tex(uMin, vMax).endVertex();
		buffer.pos(x+wid, y+hei, 0).tex(uMax, vMax).endVertex();
		buffer.pos(x+wid, y, 0).tex(uMax, vMin).endVertex();
		Tessellator.getInstance().draw();


		GL11.glPopAttrib();

		//AdvancedMacros.getMinecraft().getTextureManager().
		//GlStateManager.popMatrix();
		//GlStateManager.popAttrib();
	}






	public static void drawPixel(int x, int y, int color){
		drawRect(x, y, x+1, y+1, color);
	}

	//Called by drawScreen, gets overridden by gui
	public boolean mouseScroll(int i){
		if(firstSubsciber!=null && firstSubsciber.onScroll(this, i)) return true;
		synchronized (inputSubscribers) {
			for (InputSubscriber inputSubscriber : inputSubscribers) {
				if(inputSubscriber.onScroll(this, i)) return true;
			}

		}
		return false;
	}

	private class KeyTime{
		int keyCode;
		char key;
		long timeSig;
		static final int validationTime = 500;
		static final int repeatDelay = 10;
		boolean dead = false;
		public KeyTime(int keyCode, char key) {
			super();
			this.keyCode = keyCode;
			this.key = key;
			this.timeSig = System.currentTimeMillis();
		}

		public boolean isValid(){
			if(!Keyboard.isKeyDown(keyCode)){
				dead=true;
				return false;}
			return System.currentTimeMillis()-timeSig>validationTime;
		}

		public void fireKeyRepeat(){
			if(isValid()){
				timeSig=System.currentTimeMillis()-validationTime+repeatDelay;
				keyRepeated(key, keyCode,repeatMod);
				repeatMod = Math.max(0, repeatMod+1);//must always be positve that all
			}
		}
	}

	public FontRenderer getFontRend() {
		return fontRend;
	}
	@Override
	public void drawCenteredString(FontRenderer fontRendererIn, String text, int x, int y, int color) {
		int wid = fontRend.getStringWidth(text);
		fontRendererIn.drawString(text, x-wid/2, y-fontRend.FONT_HEIGHT/2, color, false);
	}


	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
		onMouseClicked(mouseX, mouseY, mouseButton);
	}
	public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
		if(nextKeyListen!=null && nextKeyListen.onMouseClick(this, mouseX, mouseY, mouseButton)){nextKeyListen = null; return true;}
		if(firstSubsciber!=null && firstSubsciber.onMouseClick(this, mouseX, mouseY, mouseButton)){return true;}
		try {
			super.mouseClicked(mouseX, mouseY, mouseButton);
		} catch (IOException e) {}
		//System.out.println("CLICK 1");
		synchronized (inputSubscribers) { 
			for (InputSubscriber inputSubscriber : inputSubscribers) {
				if(inputSubscriber.onMouseClick(this, mouseX, mouseY, mouseButton))
					return true;;
			}
		}
		return false;
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int state) {
		onMouseReleased(mouseX, mouseY, state);
	}
	public boolean onMouseReleased(int mouseX, int mouseY, int state) {
		super.mouseReleased(mouseX, mouseY, state);
		if(firstSubsciber!=null && firstSubsciber.onMouseRelease(this, mouseX, mouseY, state)){return true;}
		synchronized (inputSubscribers) {
			for (InputSubscriber inputSubscriber : inputSubscribers) {
				if(inputSubscriber.onMouseRelease(this, mouseX, mouseY, state))
					return true;
			}
		}
		return false;
	}
	@Override
	protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
		onMouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
	}
	public boolean onMouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
		super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
		if(firstSubsciber!=null && firstSubsciber.onMouseClickMove(this, mouseX, mouseY, clickedMouseButton, timeSinceLastClick)) {return true;}
		synchronized (inputSubscribers) {
			for (InputSubscriber inputSubscriber : inputSubscribers) {
				if(inputSubscriber.onMouseClickMove(this, mouseX, mouseY, clickedMouseButton, timeSinceLastClick))
					return true;
			}
		}
		return false;
	}

	public static interface Focusable{
		public boolean isFocused();
		public void setFocused(boolean f);
	}
	public static interface InputSubscriber{ 
		/**@param i scroll amount*/
		public boolean onScroll(Gui gui, int i);

		public boolean onMouseClick(Gui gui, int x, int y, int buttonNum);
		public boolean onMouseRelease(Gui gui, int x, int y, int state);
		public boolean onMouseClickMove(Gui gui, int x, int y, int buttonNum, long timeSinceClick);
		/**aka keyTyped*/
		public boolean onKeyPressed(Gui gui, char typedChar, int keyCode);
		/**@param typedChar char value of typed key<br>
		 * @param keycode   number for key, typed char cant  have [up arrow] for example<br>
		 * @param repeatMod for reducing the number of repeat events, you can use % on this and pick say 1 in 3 events to use*/
		public boolean onKeyRepeat(Gui gui, char typedChar, int keyCode, int repeatMod);
		public boolean onKeyRelease(Gui gui, char typedChar, int keyCode);

	}

	public void showGui(){
		//AdvancedMacros.lastGui = this;
		AdvancedMacros.getMinecraft().displayGuiScreen(this);
	}
	//something to call each time you switch back
	public void onOpen(){

	}

	public Object getFocusItem() {
		//System.out.println("Foooocas "+focusItem);
		return focusItem;
	}
	public void setFocusItem(Object focusItem) {
		this.focusItem = focusItem;
		//System.out.println("FOCUS: >> "+focusItem);
	}
	public int getUnscaledWindowWidth(){
		return AdvancedMacros.getMinecraft().displayWidth;
	}
	public int getUnscaledWindowHeight(){
		return AdvancedMacros.getMinecraft().displayHeight;
	}

	public void setDrawDefaultBackground(boolean drawDefaultBackground) {
		this.drawDefaultBackground = drawDefaultBackground;
	}
	public boolean getDrawDefaultBackground() {
		return drawDefaultBackground;
	}



	public void addDrawable(Drawable d) {
		synchronized (drawableToAdd) {
			drawableToAdd.add(d);
		}
	}
	public void addInputSubscriber(InputSubscriber i) {
		synchronized (inputSubscriberToAdd) {
			inputSubscriberToAdd.add(i);
		}
	}
	public void removeDrawables(Drawable d) {
		synchronized (drawableToRemove) {
			drawableToRemove.add(d);
		}
	}
	public void removeInputSubscriber(InputSubscriber i) {
		synchronized (inputSubscribersToRemove) {
			inputSubscribersToRemove.add(i);
		}
	}
	public void clearDrawables() {
		synchronized (drawableToRemove) {
			drawableToRemove.addAll(drawables);
		}
	}
	public void clearInputSubscribers() {
		synchronized (inputSubscribersToRemove) {
			inputSubscribersToRemove.addAll(inputSubscribers);
		}
	}
	/**
	 * Synchronize usage on linked list!
	 * do not use to add or remove elements directly
	 * */
	protected LinkedList<InputSubscriber> getInputSubscribers() {
		return inputSubscribers;
	}
}