package com.siemens.bt.jazz.services.ccm.util;

import com.ibm.team.process.common.*;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.workitem.common.IWorkItemCommon;
import com.ibm.team.workitem.common.model.IWorkItemHandle;
import com.ibm.team.workitem.common.model.ItemProfile;
import org.eclipse.core.runtime.IProgressMonitor;
import java.util.ArrayList;
import java.util.List;

public class ProcessHelper {

    public static List<IProcessAreaHandle> getProcessAreasHierarchical(IProcessArea processArea, IWorkItemCommon workItemCommon, IProgressMonitor monitor, IWorkItemHandle workItem) throws TeamRepositoryException {
        List<IProcessAreaHandle> processAreas = new ArrayList<>();
        IProjectArea projectArea = (IProjectArea) workItemCommon
                .getAuditableCommon().resolveAuditable(processArea.getProjectArea(),
                        ItemProfile.PROJECT_AREA_FULL, monitor);

        // Get the hierarchy to be able to find the process area parents
        ITeamAreaHierarchy hierarchy = projectArea.getTeamAreaHierarchy();

        IProcessAreaHandle processAreaHandle = workItemCommon.findProcessArea(workItem, monitor);

        do {
            // If this is a team area, add it and look for the parent area
            if (processAreaHandle instanceof ITeamAreaHandle) {
                processAreas.add(processAreaHandle);
                try {
                    // Try to get the parent process area
                    processAreaHandle = hierarchy.getParent((ITeamAreaHandle) processAreaHandle);
                } catch (TeamAreaHierarchyException e) {
                    // this should not happen, if it does, stop the loop
                    return processAreas;
                }
            } else if (processAreaHandle instanceof IProjectAreaHandle) {
                // If the area is the project area, we are done
                processAreas.add(processAreaHandle);
                return processAreas;
            }
            //null means project area
        } while (processAreaHandle != null);
        processAreas.add(projectArea.getProjectArea());
        return processAreas;
    }
}
