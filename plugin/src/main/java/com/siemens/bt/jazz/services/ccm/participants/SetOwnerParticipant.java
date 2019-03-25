package com.siemens.bt.jazz.services.ccm.participants;

import com.ibm.team.process.common.IProcessArea;
import com.ibm.team.process.common.IProcessAreaHandle;
import com.ibm.team.process.common.IProcessConfigurationElement;
import com.ibm.team.process.common.advice.AdvisableOperation;
import com.ibm.team.process.common.advice.IReportInfo;
import com.ibm.team.process.common.advice.runtime.IOperationParticipant;
import com.ibm.team.process.common.advice.runtime.IParticipantInfoCollector;
import com.ibm.team.process.internal.common.rest.ContributorDTO;
import com.ibm.team.process.internal.common.rest.PagedMembersDTO;
import com.ibm.team.process.internal.common.rest.ProcessRoleDTO;
import com.ibm.team.process.internal.service.web.IProcessWebUIService;
import com.ibm.team.repository.common.IContributorHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.service.IContributorService;
import com.ibm.team.repository.service.AbstractService;
import com.ibm.team.workitem.common.ISaveParameter;
import com.ibm.team.workitem.common.IWorkItemCommon;
import com.ibm.team.workitem.common.model.IAttachment;
import com.ibm.team.workitem.common.model.IWorkItem;
import com.ibm.team.workitem.common.model.ItemProfile;
import com.ibm.team.workitem.service.IWorkItemServer;
import com.siemens.bt.jazz.services.ccm.util.InfoCollectorException;
import com.siemens.bt.jazz.services.ccm.util.InfoCollectorSeverity;
import com.siemens.bt.jazz.services.ccm.util.ProcessHelper;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

import java.util.List;

public class SetOwnerParticipant extends AbstractService implements
        IOperationParticipant {

    private String roleId;

    public void run(AdvisableOperation operation,
                    IProcessConfigurationElement participantConfig,
                    IParticipantInfoCollector collector, IProgressMonitor monitor) {
        Object operationData = operation.getOperationData();

        if (operationData instanceof ISaveParameter) {

            //get Data
            ISaveParameter saveParameter = (ISaveParameter) operationData;
            if ((saveParameter.getNewState() instanceof IAttachment))
                return; //don't do anything if an attachment is uploaded(null exp!)


            IProcessArea processAreaNew = saveParameter.getNewProcessArea();
            IProcessArea processAreaOld = saveParameter.getOldProcessArea();

            IWorkItem oldWorkItem = (IWorkItem) saveParameter.getOldState();
            IWorkItem newWorkItem = (IWorkItem) saveParameter.getNewState();


            String method = resolveConfig(participantConfig, newWorkItem.getWorkItemType());

            switch (method) {
                case "Role":
                    setCCMOwner(collector, monitor, processAreaNew, processAreaOld, oldWorkItem, newWorkItem);
                    break;
                case "Creator":
                    setCurrentOwner(collector, monitor, processAreaOld, newWorkItem);
                    break;
                default:
                    return; // nothing to do, no work item change needed
            }


        }
    }

    private String resolveConfig(IProcessConfigurationElement participantConfig, String workItemType) {
        IProcessConfigurationElement[] WIs = participantConfig.getChildren();
        for (IProcessConfigurationElement wi : WIs) {
            if(wi.getAttribute("type").equals(workItemType)){
                if(wi.getAttributeNames().length>=3){
                    roleId = wi.getAttribute("role");
                }
                return wi.getAttribute("method");
            }
        }
        return "";
    }

    private void setCurrentOwner(IParticipantInfoCollector collector, IProgressMonitor monitor, IProcessArea processAreaOld, IWorkItem newWorkItem) {
        //only do stuff if wi is new and owner unassigned
        if (!newWorkItem.getOwner().getItemId().getUuidValue().equals("_YNh4MOlsEdq4xpiOKg5hvA") || !(processAreaOld == null)) {
            return;
        }

        try {

            IContributorHandle currentUser = getAuthenticatedContributor();

            if (currentUser == null) {
                return;
            }
            IWorkItemServer workItemServer = getService(IWorkItemServer.class);
            IWorkItem workingCopy = createWorkingCopy(newWorkItem, workItemServer, monitor);
            workingCopy.setOwner(currentUser);
            saveWorkingCopy(workingCopy, newWorkItem, workItemServer);

        } catch (TeamRepositoryException | InfoCollectorException e) {
            IReportInfo info = collector.createInfo("Unexpected Error:" + e.getMessage(), e.getMessage());
            info.setSeverity(InfoCollectorSeverity.ERROR.getSeverity());
            collector.addInfo(info);
        }
    }

    private void setCCMOwner(IParticipantInfoCollector collector, IProgressMonitor monitor, IProcessArea processAreaNew, IProcessArea processAreaOld, IWorkItem oldWorkItem, IWorkItem newWorkItem) {
        //if (wi is new  OR if cat didn't change OR if owner did change) And owner isn't unassigned don't do anything
        if (!newWorkItem.getOwner().getItemId().getUuidValue().equals("_YNh4MOlsEdq4xpiOKg5hvA") && (processAreaOld == null
                || (processAreaOld != null && (newWorkItem.getCategory().sameItemId(oldWorkItem.getCategory())
                || !newWorkItem.getOwner().sameItemId(oldWorkItem.getOwner()))))) {
            return;
        }
        //set up
        IWorkItemServer workItemServer = getService(IWorkItemServer.class);

        try {

            //get ccm of pa, null if none
            IContributorHandle theCCM = getConbyRole(processAreaNew, roleId);//getInheritedCCM(processAreaNew, roleName);
            if (theCCM == null) {
                IWorkItemCommon workItemCommon = getService(IWorkItemCommon.class);
                List<IProcessAreaHandle> processAreasHierarchical = ProcessHelper.getProcessAreasHierarchical(processAreaNew, workItemCommon, monitor, newWorkItem);
                for (int i = 1; i < processAreasHierarchical.size(); i++) {
                    IProcessAreaHandle currentPaHandle = processAreasHierarchical.get(i);
                    IProcessArea currentPa = (IProcessArea) workItemServer.getAuditableCommon().resolveAuditable(currentPaHandle, ItemProfile.createFullProfile(IProcessArea.ITEM_TYPE), monitor);
                    theCCM = getConbyRole(currentPa, roleId);
                    if (theCCM != null) {
                        break;
                    }
                }
            }
            if (theCCM == null) {
                return;
            }
            IWorkItem workingCopy = createWorkingCopy(newWorkItem, workItemServer, monitor);
            workingCopy.setOwner(theCCM);
            saveWorkingCopy(workingCopy, newWorkItem, workItemServer);

        } catch (TeamRepositoryException | InfoCollectorException e) {
            IReportInfo info = collector.createInfo("Unexpected Error:" + e.getMessage(), e.getMessage());
            info.setSeverity(InfoCollectorSeverity.ERROR.getSeverity());
            collector.addInfo(info);
        }
    }

    //fail-safe if internal stuff changes
    private IContributorHandle getConbyRole(IProcessArea processAreaNew, String roleName) {
        try {
            return getNonInheritedCCM(processAreaNew, roleName);
        } catch (TeamRepositoryException e) {
            return getInheritedCCM(processAreaNew, roleName);
        }
    }

    private IContributorHandle getInheritedCCM(IProcessArea processAreaNew, String roleName) {
        IContributorHandle[] members = processAreaNew.getMembers();
        for (IContributorHandle memberHandle : members) {
            String[] roleAssignmentIds = processAreaNew.getRoleAssignmentIds(memberHandle);
            for (String roleAssignmentId : roleAssignmentIds) {
                if (roleAssignmentId.equalsIgnoreCase(roleName)) {
                    return memberHandle;
                }
            }
        }
        return null; //if no ccm
    }

    //returns first member with role defined in pa, if any
    //ignores inherited Roles
    private IContributorHandle getNonInheritedCCM(IProcessArea processAreaNew, String roleId) throws TeamRepositoryException {
        IProcessWebUIService processWebUIService = getService(IProcessWebUIService.class);
        IContributorService contributorService = getService(IContributorService.class);
        IProcessWebUIService.ParmsGetMembers memberParms = new IProcessWebUIService.ParmsGetMembers();
        memberParms.processAreaItemId = processAreaNew.getItemId().getUuidValue();
        PagedMembersDTO membersPaged = processWebUIService.getMembersPaged(memberParms); //use mostly the default parms
        List members = membersPaged.getMembers().getElements();
        for (Object member : members) {
            ContributorDTO contributor = (ContributorDTO) member;
            for (Object role : contributor.getProcessRoles()) {
                if (((ProcessRoleDTO) role).getId().equals(roleId)) {
                    return contributorService.fetchContributorByUserId(contributor.getUserId());
                }
            }
        }
        return null;
    }

    private void saveWorkingCopy(IWorkItem workingCopy, IWorkItem origWorkItem, IWorkItemServer workItemServer) throws TeamRepositoryException, InfoCollectorException {
        //save wi
        IStatus saveStatus = workItemServer.saveWorkItem2(workingCopy, null, null);
        if (!saveStatus.isOK()) {
            throw new InfoCollectorException("Unable to set value.", "Unable to save the work item '" + origWorkItem.getItemId() + "'", InfoCollectorSeverity.ERROR);
        }
    }

    private IWorkItem createWorkingCopy(IWorkItem origWorkItem, IWorkItemServer workItemServer, IProgressMonitor monitor) throws TeamRepositoryException {
        // Get the full state of the work item so we can edit it
        return (IWorkItem) workItemServer
                .getAuditableCommon()
                .resolveAuditable(origWorkItem, IWorkItem.FULL_PROFILE, monitor)
                .getWorkingCopy();
    }

}
