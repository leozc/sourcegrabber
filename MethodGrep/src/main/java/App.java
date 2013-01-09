import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.EmptyVisitor;
import org.objectweb.asm.commons.Method;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class App {
    private String targetClass;
    private Method targetMethod;
    private AppClassVisitor cv;

    public boolean isdebug;
    private Set<Callee> callees = new HashSet<Callee>();

    private static class Callee {
        String className;
        String methodName;
        String methodDesc;
        String source;
        int line;

        public Callee(String cName, String mName, String mDesc, String src, int ln) {
            className = cName; methodName = mName; methodDesc = mDesc; source = src; line = ln;
        }
        @Override
        public int hashCode(){
             return (className+methodDesc+methodName+source+line).hashCode();
        }

    }
    private void log(String msg){
        if(isdebug)
            System.err.println(msg);

    }
    private class AppMethodVisitor extends MethodAdapter {

        boolean callsTarget;
        int line;

        public AppMethodVisitor() { super(new EmptyVisitor()); }

        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            //System.out.println(owner + "=>"+name+"=>"+desc + ":" + targetClass+"=>methodname=>"+targetMethod.getDescriptor() );
            if ( owner.contains(targetClass)
                 && name.equals(targetMethod.getName())
                 && desc.equals(targetMethod.getDescriptor())
                    )
            {
                callsTarget = true;
            }
        }

        public void visitCode() {
            callsTarget = false;
        }

        public void visitLineNumber(int line,  org.objectweb.asm.Label  start) {
            this.line = line;
        }

        public void visitEnd() {
            if (callsTarget)
                callees.add(new Callee(cv.className, cv.methodName, cv.methodDesc,
                        cv.source, line));
        }
    }

    private class AppClassVisitor extends ClassAdapter {

        private AppMethodVisitor mv = new AppMethodVisitor();

        public String source;
        public String className;
        public String methodName;
        public String methodDesc;

        public AppClassVisitor() { super(new EmptyVisitor()); }

        public void visit(int version, int access, String name,
                          String signature, String superName, String[] interfaces) {
            className = name;
        }

        public void visitSource(String source, String debug) {
            this.source = source;
        }

        public MethodVisitor visitMethod(int access, String name,
                                         String desc, String signature,
                                         String[] exceptions) {
            methodName = name;
            methodDesc = desc;
            //System.out.println(name + "=>"+desc);
            return mv;
        }
    }


    public void findCallingMethodsInJar(String jarPath, String targetClass,
                                        String targetMethodDeclaration) throws Exception {

        this.targetClass = targetClass;
        this.targetMethod = Method.getMethod(targetMethodDeclaration);

        this.cv = new AppClassVisitor();

        JarFile jarFile = new JarFile(jarPath);
        Enumeration<JarEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();

            try{
            if (entry.getName().endsWith(".class")) {

                InputStream stream = new BufferedInputStream(jarFile.getInputStream(entry), 4096);
                ClassReader reader = new ClassReader(stream);

                reader.accept(cv, 0);

                stream.close();
            }
            }catch (Exception e){
                System.err.println("Error in "+entry.getName() + " "+e.getMessage());
            }

        }
    }


    public static void main( String[] args ) {

        if(args.length<3){
            System.err.println("It takes three parameters $LIB $CLASS $MethodDesc");
            System.err.println("See run.sh for example");
            System.exit(-1);

        }
        try {
            App app = new App();

            String classname = args[0];
            String methoddesc = args[1];

            System.out.println("CLASS: "+classname);
            System.out.println("MethodDesc: "+methoddesc);
            System.out.println("LIBs: "+ StringUtils.join(Arrays.asList(args).subList(2,args.length-1), ";"));

            for(int i=2;i<args.length;i++){   //for each libs
                for( String lib : StringUtils.split( args[i],";")){
                    System.out.println("-- "+lib);
                    try{
                        app.findCallingMethodsInJar(lib, classname, methoddesc);

                        for (Callee c : app.callees) {
                            System.out.println(">> "+c.className+ "/"+ c.source+":"+c.line+" "+c.methodName+" "+c.methodDesc + ":calls "+classname +"@"+methoddesc);
                        }

                        System.out.println("--\n"+app.callees.size()+" methods invoke "+
                                app.targetClass+" "+
                                app.targetMethod.getName()+" "+app.targetMethod.getDescriptor());
                    }catch (Exception e){
                        System.err.println("ERROR: "+lib+":"+e.getMessage() );
                        ;
                    }
                }
            }


        } catch(Exception x) {
            x.printStackTrace();
        }
    }

}
