import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ModificationWatchpointRequest;
import com.sun.jdi.Location;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;


/*
Supported Variable Watch: Field Variables + Local Variables (stack memory)

Creates a nice GUI for users.
 */

public class MainMon {
    static ThreadReference main;
    static DefaultTableModel model;
    public static void main(String[] args) throws Exception {
        VirtualMachine vm = null;

        model = new DefaultTableModel();

        JFrame frame = new JFrame("Debug Variables");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);


        JTable table = new JTable(model);
        model.addColumn("Variable");
        model.addColumn("Value");
        model.addColumn("Type");


        JScrollPane scrollPane = new JScrollPane(table);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.setSize(100, 300);

        frame.pack();
        frame.setVisible(true);

        try {
            vm = new Connections().connect("localhost", 7777);
        }catch(Exception e){
            e.printStackTrace();
        }

        for(ThreadReference tRef : vm.allThreads()){
            //just use main thread for now...
            if(tRef.name().equals("main")){
                System.out.println("main thread acquired");
                main = tRef;

            }

        }

        for (ReferenceType refer : vm.allClasses()) {

            if(refer.toString().contains("loaded by instance of")) {
                //load only classes that are by us
                ReferenceType refType = refer;

                //do some of our fun stuff
                //very chaotic.. breakpoint every single line, and then we can examine ALL LINES!
                EventRequestManager erm = vm.eventRequestManager();
                for(Location loc : refType.allLineLocations()){
                    BreakpointRequest br = erm.createBreakpointRequest(loc);
                    br.setEnabled(true);

                }




                //go through our classes, add a field hook to it

                List<Field> ff = refType.fields();
                System.out.println(ff.size());
                addFieldWatch(vm, refType);
                System.out.println(refType.allFields());
            }

        }


        System.out.println("name="+vm.name());
        System.out.println("description="+vm.description());





        //extra watchpoints!



        vm.resume();
        listenForData(vm);
    }

    private static void addFieldWatch(VirtualMachine vm, ReferenceType refType) {
        EventRequestManager erm = vm.eventRequestManager();
        List<Field> rr = refType.allFields();


        Field field = null;
        for(Field ll : rr) {
           // System.out.println("Getting Watchpoint for Variable: " + field + " TYPE: " + field.typeName());
            //apparntly strings decided to freak out
            field = ll;



            if (field == null) System.err.println("field is null");
            ModificationWatchpointRequest modificationWatchpointRequest = erm.createModificationWatchpointRequest(field);
            modificationWatchpointRequest.setEnabled(true);
        }
    }

    public static void displayCallStack() throws Exception{
        main.suspend();

        System.out.println("Stack Frame: ");
        for(int i=0; i<main.frameCount(); i++){
            StackFrame cFrame = main.frame(i);
            System.out.println(cFrame);
        }

        main.resume();


    }
    public static void addToTable(String name, String value, String type){
        boolean rrrr = false;
        int colAt = 0;

        for(int i=0; i<model.getRowCount(); i++){
            if(model.getValueAt(i, 0) == name){

                rrrr = true;
                colAt = i;
                break;
            }
        }

        if(rrrr){
            model.setValueAt(value,colAt, 1);
        }else{
            model.addRow(new Object[]{name, value, type});
        }
    }


    public static void listenForData(VirtualMachine vm) throws Exception {
        EventQueue eventQueue = vm.eventQueue();
        while (true) {
            //displayCallStack();

            EventSet eventSet = eventQueue.remove();



            for (Event ev : eventSet) {

                    if(ev instanceof BreakpointEvent){
                        BreakpointEvent breakpointEvt = (BreakpointEvent) ev;
                        StackFrame stackFrame = breakpointEvt.thread().frame(0);
                        for(LocalVariable localVar : stackFrame.visibleVariables()) {
                            Value val = null;
                            try {
                                val = stackFrame.getValue(localVar);
                                addToTable(localVar.name(), val.toString(), localVar.typeName());

                            } catch (Exception e) {
                                System.out.println(ev);
                                //e.printStackTrace();
                                //sweep it under the rug....
                            }
                            System.out.println("VARIABLE ON STACK: " + val);

                        }

                    }

                    if(ev instanceof ModificationWatchpointEvent){
                        //THERE IS MOVEMENT!!!!

                        ModificationWatchpointEvent me = (ModificationWatchpointEvent) ev;

                       addToTable(me.field().name(), me.valueToBe().toString(), me.field().typeName());

                        System.out.println(me);
                        System.out.println("Variable: " + me.field().name());
                        System.out.println("Value: " + me.valueToBe());

                        System.out.println();



                    }

            }

            //resume la thread
            eventSet.resume();
        }
    }
}
