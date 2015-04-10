package org.esa.beam.ui.tooladapter.utils;

import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.OperatorSpiRegistry;
import org.esa.beam.framework.gpf.descriptor.ToolAdapterOperatorDescriptor;
import org.esa.beam.framework.gpf.operators.tooladapter.ToolAdapterOpSpi;
import org.esa.beam.ui.tooladapter.interfaces.ToolAdapterItemAction;
import org.esa.snap.rcp.SnapDialogs;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.OnStart;

import java.io.IOException;
import java.util.*;

/**
 * This runnable should be invoked when the Tool Adapter module starts, and will
 * register the available adapters as menu actions.
 *
 * @author Cosmin Cara
 */

public class ToolAdapterMenuRegistrar {

    private static final String MENU_PATH = "Menu/Tools";
    private static final String MENU_TEXT = "External tools";

    private static final Map<String, ToolAdapterOperatorDescriptor> actionMap = new HashMap<>();

    public static Map<String, ToolAdapterOperatorDescriptor> getActionMap() {
        return actionMap;
    }

    public static void registerOperatorMenu(ToolAdapterOperatorDescriptor operator) {
        registerOperatorMenu(operator, MENU_TEXT, MENU_PATH);
    }

    public static void registerOperatorMenu(ToolAdapterOperatorDescriptor operator, String groupName, String menu) {
        FileObject menuFolder = FileUtil.getConfigFile(menu);
        try {
            FileObject groupItem = menuFolder.getFileObject(groupName);
            if (groupItem == null) {
                groupItem = menuFolder.createFolder(groupName);
                groupItem.setAttribute("position", 1001);
            }

            String operatorAlias = operator.getAlias();
            FileObject newItem = groupItem.getFileObject(operatorAlias, "instance");
            if (newItem != null) {
                newItem.delete();
            }
            newItem = groupItem.createData(operatorAlias, "instance");
            ToolAdapterItemAction action = new ToolAdapterItemAction(operatorAlias);
            newItem.setAttribute("instanceCreate", action);
            newItem.setAttribute("instanceClass", action.getClass().getName());
            actionMap.put(operatorAlias, operator);
        } catch (IOException e) {
            SnapDialogs.showError("Error:" + e.getMessage());
        }
    }

    @OnStart
    public static class StartOp implements Runnable {
        @Override
        public void run() {
            OperatorSpiRegistry spiRegistry = GPF.getDefaultInstance().getOperatorSpiRegistry();
            if (spiRegistry != null) {
                Set<OperatorSpi> operatorSpis = spiRegistry.getOperatorSpis();
                if (operatorSpis != null) {
                    operatorSpis.stream().filter(spi -> spi instanceof ToolAdapterOpSpi).forEach(spi -> {
                        ToolAdapterOperatorDescriptor operatorDescriptor = (ToolAdapterOperatorDescriptor) spi.getOperatorDescriptor();
                        registerOperatorMenu(operatorDescriptor, MENU_TEXT, MENU_PATH);
                    });
                }
            }
        }
    }
}