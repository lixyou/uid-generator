package com.baidu.fsg.uid.worker;

import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkNodeExistsException;
import org.apache.commons.lang.StringUtils;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 基于zookeeper实现workId分配器
 *
 * @author huaijin
 */
public class WorkerIdAssignerBaseZookeeper implements WorkerIdAssigner, InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerIdAssignerBaseZookeeper.class);

    private String zkServer;

    private int sessionTimeout;

    private int connectionTimeout;

    private ZkClient zkClient;

    private static final String ZK_PATH_SEPARATOR = "/";

    private static final String DEFAULT_PARENT_PATH = ZK_PATH_SEPARATOR + "uid";

    private static final String STORAGE_PATH = DEFAULT_PARENT_PATH + ZK_PATH_SEPARATOR + "storage";

    private static final String WORK_NODE_PATH = DEFAULT_PARENT_PATH + ZK_PATH_SEPARATOR +  "workNode";

    private static final String WORK_ID_PREFIX = WORK_NODE_PATH + ZK_PATH_SEPARATOR + "workid-";

    private static final String EMPTY_STRING = "";


    @Override
    public long assignWorkerId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        String workNodePath = STORAGE_PATH + ZK_PATH_SEPARATOR + host;
        String workNode = null;
        if (zkClient.exists(workNodePath)) {
            workNode = zkClient.readData(workNodePath);
        }
        if (StringUtils.isEmpty(workNode)) {
            workNode = createNode(WORK_ID_PREFIX, EMPTY_STRING, CreateMode.PERSISTENT_SEQUENTIAL);
            createNode(workNodePath, EMPTY_STRING, CreateMode.PERSISTENT);
            zkClient.writeData(workNodePath, workNode);
        }
        LOGGER.info("The workNode is: [" + workNode + "].");
        return getWorkIdSubPrefix(workNode);
    }

    /**
     * First init the zkClient, create "/uid" "/uid/workId-" "/uid/storage" nodes in proper order.
     *
     * @throws Exception
     * @author huaijin
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        zkClient = new ZkClient(zkServer, sessionTimeout, connectionTimeout);

        // create the default root path for uid-generator
        createNode(DEFAULT_PARENT_PATH, EMPTY_STRING, CreateMode.PERSISTENT);
        // create the path for work nodes
        createNode(WORK_NODE_PATH, EMPTY_STRING, CreateMode.PERSISTENT);
        // create the path for storing workid.
        createNode(STORAGE_PATH, EMPTY_STRING, CreateMode.PERSISTENT);
    }

    /**
     * create the zkNode.
     *
     * @param path       the  path of node
     * @param data       the  data of node
     * @param createMode the type of node
     * @return
     * @author huaijin
     */
    private String createNode(String path, Object data, CreateMode createMode) {
        String createdPath = path;
        if (!zkClient.exists(path)) {
            try {
                createdPath = zkClient.create(path, data, createMode);
                LOGGER.info("The path for [" + path + "] is successfully created.");
            } catch (ZkNodeExistsException e) {
                // ignore
                LOGGER.info("The path for [" + path + "] already exists.");
            }
        }
        return createdPath;
    }

    private Long getWorkIdSubPrefix(String workNodePath) {
        String workId = workNodePath.substring(WORK_ID_PREFIX.length());
        return Long.parseLong(workId);
    }

    public String getZkServer() {
        return zkServer;
    }

    public void setZkServer(String zkServer) {
        this.zkServer = zkServer;
    }

    public int getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(int sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }
}
