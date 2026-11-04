@Library('wf-ci') _

wfBuild(
    repo: 'WFMedical',
    upstream: ['WfLib/main'],
    jdk: '21',
    branches: ['neoforge-1.21.1'],
    build: 'build --no-daemon --stacktrace',
    publish: 'publish --no-daemon --stacktrace',
    version: [branches: ['main'], key: 'mod_version', modIdKey: 'mod_id', jdk: '17', artifact: 'build/libs/@MOD_ID@-@VERSION@.jar'],
    tagRolling: [
        tag: 'wf', jdk: '17', build: 'build -x test --no-daemon',
        stage: 'cp "$(find build/libs -maxdepth 1 -name \'*.jar\' ! -name \'*-sources*\' ! -name \'*-slim*\' ! -name \'*-dev*\' | head -1)" wfmedical.jar',
        filesCmd: 'echo wfmedical.jar',
    ],
)
