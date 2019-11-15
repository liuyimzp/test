package com.yinhai.cloud.core.api.util;

import com.yinhai.cloud.core.api.entity.MsgVO;
import com.yinhai.cloud.core.api.exception.SSHConnectionException;
import com.yinhai.cloud.core.api.exception.SSHExecuteException;
import com.yinhai.cloud.core.api.handler.SSHExecResultHandler;
import com.yinhai.cloud.core.api.handler.SSHExecRunningHandler;
import com.yinhai.cloud.core.api.ssh.ExecResult;
import com.yinhai.cloud.core.api.ssh.SSHConnection;
import com.yinhai.cloud.core.api.ssh.command.*;
import com.yinhai.cloud.core.api.vo.ConnVo;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * @author jianglw
 */
public class CommonSshExecUtil {
    private static final String ROOT_USERNAME = "root";
    private static final String File_SERPARATOR = "/";

    /**
     * 批量执行命令行、脚本、上传文件等操作：按照commands传入的顺序执行
     *
     * @param vo
     * @param commands
     * @return
     */
    public static HashMap<AbstractCommand, MsgVO> exec(final ConnVo vo, AbstractCommand... commands) throws SSHExecuteException, SSHConnectionException {
        return exec(null, null, vo, commands);
    }

    /**
     * 批量执行命令行、脚本、上传文件等操作：按照commands传入的顺序执行
     *
     * @param connInfo
     * @param commands
     * @return
     */
    public static HashMap<AbstractCommand, MsgVO> exec(SSHExecRunningHandler runningHandler, SSHExecResultHandler resultHandler, final ConnVo connInfo, AbstractCommand... commands) throws SSHExecuteException, SSHConnectionException {
        final HashMap<AbstractCommand, MsgVO> result = new HashMap<>();
        SSHConnection conn = new SSHConnection(connInfo);
        conn.openConnection();
        try {
            for (final AbstractCommand command : commands) {

                MsgVO msgVO = new MsgVO();
                if (command instanceof ConsoleCommand) {
                    ExecResult execResult = conn.exec(command, runningHandler);
                    msgVO.setSuccess(execResult.isSuccess());
                    msgVO.setSysoutMsg(execResult.getSystemOut());
                    msgVO.setErrorMsg(execResult.getErrorOut());
                } else if (command instanceof ShellCommand) {
                    ShellCommand shellCmd = (ShellCommand) command;
                    if (shellCmd.getShellContent() != null && !"".equals(shellCmd.getShellContent())) {
                        // 有脚本内容，则先要执行文件上传
                        InputStream is = IOUtils.toInputStream(shellCmd.getShellContent(), StandardCharsets.UTF_8.name());
                        String remoteFilePath = (shellCmd.getShellServerWorkDir() + File_SERPARATOR + shellCmd.getShellName()).replaceAll("/+", "/");
                        conn.uploadFileToServer(is, remoteFilePath);

                    }
                    ConsoleCommand chmodWithExecutable = new ConsoleCommand();
                    chmodWithExecutable.appendCommand("chmod +x " + shellCmd.getShellServerWorkDir() + "/" + shellCmd.getShellName());
                    conn.exec(chmodWithExecutable, null);
                    ExecResult execResult = conn.exec(command, runningHandler);
                    msgVO.setSuccess(execResult.isSuccess());
                    msgVO.setSysoutMsg(execResult.getSystemOut());
                    msgVO.setErrorMsg(execResult.getErrorOut());

                } else if (command instanceof UploadFileCommand) {
                    UploadFileCommand uploadFileCommand = (UploadFileCommand) command;
                    conn.uploadFileToServer(uploadFileCommand.getLocalFileAbsolutePath(), uploadFileCommand.getRemoteFileAbsolutePath());
                    msgVO.setSysoutMsg("上传文件" + uploadFileCommand.getLocalFileAbsolutePath() + "->" + uploadFileCommand.getRemoteFileAbsolutePath() + "成功！");
                    msgVO.setSuccess(true);
                } else if (command instanceof UploadContentCommand) {
                    UploadContentCommand upload = (UploadContentCommand) command;
                    conn.uploadFileToServer( IOUtils.toInputStream(upload.getContent(), StandardCharsets.UTF_8.name()), upload.getRemoteFileAbsolutePath());
                    msgVO.setSysoutMsg("写服务器文件"+ upload.getRemoteFileAbsolutePath() + "成功！");
                    msgVO.setSuccess(true);
                }else if (command instanceof UploadStreamCommand) {
                    UploadStreamCommand upload = (UploadStreamCommand) command;
                    conn.uploadFileToServer( upload.getStream(), upload.getRemoteFileAbsolutePath());
                    msgVO.setSysoutMsg("写服务器文件"+ upload.getRemoteFileAbsolutePath() + "成功！");
                    msgVO.setSuccess(true);
                }else if (command instanceof DownloadFileCommand) {
                    DownloadFileCommand downloadFileCommand = (DownloadFileCommand) command;
                    conn.downloadFileFromServer(downloadFileCommand.getRemoteFileAbsolutePath(), downloadFileCommand.getLocalFileAbsolutePath());
                    msgVO.setSysoutMsg("下载文件" + downloadFileCommand.getRemoteFileAbsolutePath() + "->" + downloadFileCommand.getLocalFileAbsolutePath() + "成功！");
                    msgVO.setSuccess(true);
                } else if (command instanceof UploadDirCommand) {
                    UploadDirCommand uploadDirCommand = (UploadDirCommand) command;
                    conn.uploadLocalDirToServer(uploadDirCommand.getLocalDir(),uploadDirCommand.getRemoteParentDir());
                    msgVO.setSysoutMsg("上传文件夹" + uploadDirCommand.getLocalDir() + "->" + uploadDirCommand.getRemoteParentDir() + "成功！");
                    msgVO.setSuccess(true);
                }else{
                    throw new SSHExecuteException("无效命令类型：" + command.getClass());
                }

                if (!msgVO.isSuccess()) {
                    throw new SSHExecuteException(msgVO.getErrorMsg());
                }
                result.put(command, msgVO);
                if (resultHandler != null) {
                    resultHandler.handleMessage(command, msgVO);
                }
            }
        } catch (Exception e) {
            String exceptionMsgContent = CommonUtil.getExceptionMsgContent(e);
            String solve = CommonUtil.solveUserPermission(exceptionMsgContent);
            String replaceWithEmptyString = "[sudo] password for "+connInfo.getUser()+":";
            String exceptionMsg  = (exceptionMsgContent + solve).replace(replaceWithEmptyString,"");
            throw new SSHExecuteException(exceptionMsg);
        } finally {
             conn.closeConnection();
        }
        return result;
    }


}
