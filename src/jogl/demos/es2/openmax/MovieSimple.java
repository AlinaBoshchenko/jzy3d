/*
 * Copyright (c) 2008 Sun Microsystems, Inc. All Rights Reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 * - Redistribution of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 * 
 * - Redistribution in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 * 
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * 
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES,
 * INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN
 * MICROSYSTEMS, INC. ("SUN") AND ITS LICENSORS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN OR
 * ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR
 * DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE
 * DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
 * ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF
 * SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 * 
 */


package demos.es2.openmax;

import javax.media.opengl.*;

import com.jogamp.opengl.util.glsl.*;
import com.jogamp.opengl.util.*;

import com.jogamp.openmax.*;

import java.nio.*;
import java.net.*;

import com.jogamp.newt.*;
import com.jogamp.newt.event.*;
import com.jogamp.newt.opengl.*;

public class MovieSimple implements MouseListener, GLEventListener, OMXEventListener {
    private GLWindow window;
    private boolean quit = false;
    private boolean rotate = false;
    private float zoom = -2.5f;
    private float ang = 0f;
    private long startTime;
    private long curTime;
    private String stream;

    public void changedAttributes(OMXInstance omx, int event_mask) {
        System.out.println("changed stream attr ("+event_mask+"): "+omx);
    }

    public void mouseClicked(MouseEvent e) {
        switch(e.getClickCount()) {
            case 2:
                quit=true;
                break;
        }
    }
    public void mouseEntered(MouseEvent e) {
    }
    public void mouseExited(MouseEvent e) {
    }
    public void mousePressed(MouseEvent e) {
    }
    public void mouseReleased(MouseEvent e) {
        rotate = false;
        zoom = -2.5f;
    }
    public void mouseMoved(MouseEvent e) {
    }
    public void mouseDragged(MouseEvent e) {
        rotate = true;
        zoom = -5;
    }
    public void mouseWheelMoved(MouseEvent e) {
    }

    public MovieSimple(String stream) {
        this.stream = stream ;
    }

    private void run() {
        System.err.println("MovieSimple.run()");
        try {
            GLCapabilities caps = new GLCapabilities(GLProfile.getGL2ES2());
            // For emulation library, use 16 bpp
            caps.setRedBits(5);
            caps.setGreenBits(6);
            caps.setBlueBits(5);
            caps.setDepthBits(16);

            window = GLWindow.create(caps);

            window.addMouseListener(this);
            window.addGLEventListener(this);
            // window.setEventHandlerMode(GLWindow.EVENT_HANDLER_GL_CURRENT); // default
            // window.setEventHandlerMode(GLWindow.EVENT_HANDLER_GL_NONE); // no current ..

            // Size OpenGL to Video Surface
            window.setFullscreen(true);
            window.setVisible(true);

            startTime = System.currentTimeMillis();
            while (!quit) {
                window.display();
            }

            // Shut things down cooperatively
            if(null!=movie) {
                movie.destroy(window.getGL());
                movie=null;
            }
            window.destroy();
            System.out.println("MovieSimple shut down cleanly.");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    ShaderState st;
    PMVMatrix pmvMatrix;

    private void initShader(GL2ES2 gl) {
        int tmpI;

        // Create & Compile the shader objects
        ShaderCode rsVp = ShaderCode.create(gl, gl.GL_VERTEX_SHADER, 1, MovieSimple.class,
                                            "shader", "shader/bin", "moviesimple");
        ShaderCode rsFp = ShaderCode.create(gl, gl.GL_FRAGMENT_SHADER, 1, MovieSimple.class,
                                            "shader", "shader/bin", "moviesimple");

        // Create & Link the shader program
        ShaderProgram sp = new ShaderProgram();
        sp.add(rsVp);
        sp.add(rsFp);
        if(!sp.link(gl, System.err)) {
            throw new GLException("Couldn't link program: "+sp);
        }

        // Let's manage all our states using ShaderState.
        st = new ShaderState();
        st.attachShaderProgram(gl, sp);
    }

    OMXInstance movie=null;

    public void init(GLAutoDrawable drawable) {
        GL2ES2 gl = drawable.getGL().getGL2ES2();
        System.err.println("Entering initialization");
        System.err.println("GL_VERSION=" + gl.glGetString(gl.GL_VERSION));
        System.err.println("GL_EXTENSIONS:");
        System.err.println("  " + gl.glGetString(gl.GL_EXTENSIONS));

        pmvMatrix = new PMVMatrix();

        initShader(gl);

        // Push the 1st uniform down the path 
        st.glUseProgram(gl, true);

        pmvMatrix.glMatrixMode(pmvMatrix.GL_PROJECTION);
        pmvMatrix.glLoadIdentity();
        pmvMatrix.glMatrixMode(pmvMatrix.GL_MODELVIEW);
        pmvMatrix.glLoadIdentity();

        if(!st.glUniform(gl, new GLUniformData("mgl_PMVMatrix", 4, 4, pmvMatrix.glGetPMvMatrixf()))) {
            throw new GLException("Error setting PMVMatrix in shader: "+st);
        }
        if(!st.glUniform(gl, new GLUniformData("mgl_ActiveTexture", 0))) {
            throw new GLException("Error setting mgl_ActiveTexture in shader: "+st);
        }
        gl.glActiveTexture(GL.GL_TEXTURE0);

        float aspect = 16.0f/9.0f;
        float xs=1f, ys=1f; // scale object
        float ss=1f, ts=1f; // scale tex-coord

        if(true) {
            xs = aspect; // b > h
            ys =     1f; // b > h
        } else {
            ss =     1f/aspect; // b > h, crop width
            ts =     1f;        // b > h
        }

        // Allocate vertex array
        GLArrayDataServer vertices = GLArrayDataServer.createGLSL(gl, "mgl_Vertex", 3, gl.GL_FLOAT, false, 4, GL.GL_STATIC_DRAW);
        {
            // Fill them up
            FloatBuffer verticeb = (FloatBuffer)vertices.getBuffer();
            verticeb.put(-1f*xs);  verticeb.put( -1f*ys);  verticeb.put( 0);
            verticeb.put(-1f*xs);  verticeb.put(  1f*ys);  verticeb.put( 0);
            verticeb.put( 1f*xs);  verticeb.put( -1f*ys);  verticeb.put( 0);
            verticeb.put( 1f*xs);  verticeb.put(  1f*ys);  verticeb.put( 0);
        }
        vertices.seal(gl, true);

        // Allocate texcoord array
        GLArrayDataServer texcoord = GLArrayDataServer.createGLSL(gl, "mgl_MultiTexCoord", 2, gl.GL_FLOAT, false, 4, GL.GL_STATIC_DRAW);
        {
            // Fill them up
            FloatBuffer texcoordb = (FloatBuffer)texcoord.getBuffer();
            texcoordb.put( 0f*ss);  texcoordb.put(  0f*ts);
            texcoordb.put( 0f*ss);  texcoordb.put(  1f*ts);
            texcoordb.put( 1f*ss);  texcoordb.put(  0f*ts);
            texcoordb.put( 1f*ss);  texcoordb.put(  1f*ts);
        }
        texcoord.seal(gl, true);

        GLArrayDataServer colors = GLArrayDataServer.createGLSL(gl, "mgl_Color",  4, gl.GL_FLOAT, false, 4, GL.GL_STATIC_DRAW);
        {
            // Fill them up
            FloatBuffer colorb = (FloatBuffer)colors.getBuffer();
            colorb.put( 0);    colorb.put( 0);     colorb.put( 0);    colorb.put( 1);
            colorb.put( 1);    colorb.put( 1);     colorb.put( 1);    colorb.put( 1);
            colorb.put( 0);    colorb.put( 0);     colorb.put( 0);    colorb.put( 1);
            colorb.put( 1);    colorb.put( 1);     colorb.put( 1);    colorb.put( 1);
        }
        colors.seal(gl, true);
        
        // OpenGL Render Settings
        gl.glClearColor(0.2f, 0.2f, 0.2f, 1);
        gl.glEnable(GL2ES2.GL_DEPTH_TEST);

        st.glUseProgram(gl, false);

        // Let's show the completed shader state ..
        System.out.println(st);

        try {
            movie = new OMXInstance();
            movie.addEventListener(this);
            movie.setStream(4, new URL(stream));
            System.out.println("p0 "+movie);
        } catch (MalformedURLException mue) { mue.printStackTrace(); }
        if(null!=movie) {
            //movie.setStreamAllEGLImageTexture2D(gl);
            //movie.activateStream();
            //System.out.println("p1 "+movie);
            movie.play();
        }
    }

    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        GL2ES2 gl = drawable.getGL().getGL2ES2();

        st.glUseProgram(gl, true);

        // Set location in front of camera
        pmvMatrix.glMatrixMode(pmvMatrix.GL_PROJECTION);
        pmvMatrix.glLoadIdentity();
        pmvMatrix.gluPerspective(45.0f, (float)width / (float)height, 1.0f, 100.0f);
        //pmvMatrix.glOrthof(-4.0f, 4.0f, -4.0f, 4.0f, 1.0f, 100.0f);

        pmvMatrix.glMatrixMode(pmvMatrix.GL_MODELVIEW);
        pmvMatrix.glLoadIdentity();
        pmvMatrix.glTranslatef(0, 0, zoom);

        GLUniformData ud = st.getUniform("mgl_PMVMatrix");
        if(null!=ud) {
            // same data object
            st.glUniform(gl, ud);
        } 

        st.glUseProgram(gl, false);
    }

    public void dispose(GLAutoDrawable drawable) {
        GL2ES2 gl = drawable.getGL().getGL2ES2();

        movie.destroy(gl);
        movie=null;
        pmvMatrix.destroy();
        pmvMatrix=null;
        st.destroy(gl);
        st=null;
        quit=true;
    }

    public void display(GLAutoDrawable drawable) {
        GL2ES2 gl = drawable.getGL().getGL2ES2();

        st.glUseProgram(gl, true);

        gl.glClear(gl.GL_COLOR_BUFFER_BIT | gl.GL_DEPTH_BUFFER_BIT);

        if(rotate) {
            curTime = System.currentTimeMillis();
            ang = ((float) (curTime - startTime) * 360.0f) / 8000.0f;
        }

        if(rotate || zoom!=0f) {
            pmvMatrix.glMatrixMode(pmvMatrix.GL_MODELVIEW);
            pmvMatrix.glLoadIdentity();
            pmvMatrix.glTranslatef(0, 0, zoom);
            pmvMatrix.glRotatef(ang, 0, 0, 1);
            // pmvMatrix.glRotatef(ang, 0, 1, 0);

            GLUniformData ud = st.getUniform("mgl_PMVMatrix");
            if(null!=ud) {
                // same data object
                st.glUniform(gl, ud);
            }

            if(!rotate) {
                zoom=0f;
            }
        }


        com.jogamp.opengl.util.texture.Texture tex = null;
        if(null!=movie) {
            tex=movie.getNextTextureID();
            if(null!=tex) {
                tex.enable();
                tex.bind();
            }
        }

        // Draw a square
        gl.glDrawArrays(gl.GL_TRIANGLE_STRIP, 0, 4);

        if(null!=tex) {
            tex.disable();
        }

        st.glUseProgram(gl, false);
    }

    public void displayChanged(GLAutoDrawable drawable, boolean modeChanged, boolean deviceChanged) {
    }

    public static void main(String[] args) {
        String fname="file:///Storage Card/resources/a.mp4";
        if(args.length>0) fname=args[0];
        new MovieSimple(fname).run();
        System.exit(0);
    }
}
