import java.text.SimpleDateFormat
node {

    stage('test'){
        echo "测试"
    }
    stage('Build') {
        echo "开始打包"
        checkout scm
        sh "mvn package  -Dmaven.test.skip=true"
        echo "打包打包完成"
       }

    // 项目打包到镜像并推送到镜像仓库
    stage('Build and Push Image') {
        def pom = readMavenPom file: 'pom.xml'
        //用户的镜像库地址
        docker_host = "192.168.17.187:5000"
        img_name = "${pom.groupId}/${pom.artifactId}"
        docker_img_name = "${docker_host}/${img_name}"
        //copy需要Dockerfile，和jdk包到当前目录
        sh "cp -rf ../../docker/jar/Dockerfile ."
        sh "cp -rf ../../docker/jar/jdk-8u171-linux-x64.tar.gz ."
        //获取当前时间作为镜像ID
        def dateFormat = new SimpleDateFormat("yyyyMMddHHmm")
        def timeStamp=dateFormat.format(new Date())
        sh "docker build -t ${docker_img_name}:${timeStamp} --build-arg docker_host=${docker_host} --build-arg packageFile=target/${pom.artifactId}-${pom.version}.jar --build-arg artifactId=${pom.artifactId}  ."
        sh "docker push ${docker_img_name}:${timeStamp}"
        echo "镜像制作完成"

    }

}