/**
 * @author YUWEI@新质战斗力未来中心
 */
'ui';


setScreenMetrics(1080, 2340);

ui.layout(
    <vertical padding="16" gravity="center_horizontal">
        
        <text text="云端办小助手" textSize="24sp" textColor="#2C3E50" gravity="center" marginBottom="14" />
        
        <horizontal gravity="center" marginTop="0">
            <text text="民警二：" textSize="16sp" />
            <spinner id="mySpin" entries="赵炜彦|陈立晟|钟少文|朱斌|黄纬绵|罗海威|李谋挺" />
        </horizontal>
        
        
        <button id="startBtn" text="开始运行" textSize="18sp" w="200" h="60" bg="#3498db" textColor="#ffffff" margin="16" />
        <button id="exportBtn" text="导出车牌" textSize="18sp" w="200" h="60" bg="#2ecc71" textColor="#ffffff" margin="16" />
        
        <vertical bg="#f0f0f0" padding="8" margin="16" w="*">
            <text text="使用说明：" textSize="16sp" textColor="#2C3E50" marginBottom="8" />
            <text text="1. 先登录[黄盾]，后进入[执法处理]模块，点击[云端办]" textSize="16sp" textColor="#34495e" />
            <text text="2. 打开[政务微信]，找到从网页端转发过来的信息" textSize="16sp" textColor="#34495e" />
            <text text="3. 前往[设置-无障碍]开启无障碍服务" textSize="16sp" textColor="#34495e" />
            <text text="4. 点击[开始运行]按钮启动程序" textSize="16sp" textColor="#34495e" />
            <text text="5. 完成后点击[导出车牌]复制已开单数据，发往政务微信做最后一步流程" textSize="16sp" textColor="#34495e" />
            <text text="6. 按音量上键可提前终止运行" textSize="16sp" textColor="#34495e" />
            
            
        </vertical>
        
        <text id="status" text="就绪，等待开始..." textSize="16sp" textColor="#7f8c8d" gravity="center" margin="30" />
        
        <horizontal gravity="bottom|center" marginTop="30">
            <text text="新质战斗力未来中心 ©2025" textSize="12sp" textColor="#95a5a6" />
        </horizontal>
    </vertical>
);

let selected = "";

ui.mySpin.setOnItemSelectedListener({
    onItemSelected: (parent, view, position, id) => {
        selected = ui.mySpin.getSelectedItem();
        //toastLog(selected);
    }
});


// // 全局变量存储识别结果
let chepaiResults = [];

//auto.waitFor(); // need??

ui.startBtn.click(() => {
    ui.status.setText("正在启动自动化任务...");
    ui.startBtn.setEnabled(false);
    ui.exportBtn.setEnabled(false);

    // // 清空之前的结果
    // ocrResults = [];

    // 在新线程中运行任务，避免阻塞UI
    threads.start(function() {
        try {
            runn();
        } catch (e) {
            // ui.status.setText("执行出错，" + e);
            //toastLog("执行出错: " + e);
            // chepaiResults.push();
        } finally {
            ui.status.setText("任务完成，可重新开始");
            ui.startBtn.setEnabled(true);
            ui.exportBtn.setEnabled(true);

            // 添加最终结果
            // ocrResults.push("========== 任务完成 ==========");
            // ocrResults.push("状态: " + ui.status.getText());
        }
    });
});

// 设置导出按钮点击事件
// chepaiResults.push();
ui.exportBtn.click(() => {
    if (chepaiResults.length === 0) {
        toast("没有可导出的结果");
        return;
    }

    // 将结果转换为字符串
    //let resultText = ocrResults.join("\n");
    let chepaiResultText = chepaiResults.join('\n');

    // 复制到剪贴板
    setClip(chepaiResultText);
    toast("结果已复制到剪贴板");

    // 可选：显示导出内容预览
    dialogs.build({
        title: "导出结果预览",
        content: chepaiResultText,
        positive: "确定",
        neutral: "复制"
        // negative: "取消"
    }).on("positive", () => {
        // 确定按钮
    }).on("neutral", () => {
        // 再次复制
        setClip(chepaiResultText);
        toast("已再次复制到剪贴板");
    }).show();
});




function runn() {

    images.requestScreenCapture();
    // images.captureScreen();
    launchApp("政务微信");
    sleep(1000);

    // i让用户输入
    //待写一个ui  开始 停止
    for (var i = 0; i < 150; i++) {
        sleep(150);

        //  toastLog(selected);
      //  if (i % 2 == 0) {
         //   swipe(700, 1100, 700, 1500, 100);
         //   swipe(700, 1500, 700, 1100, 100);
          //  sleep(500);
      //  }



        //  break;
        let weifacheliang = ocrr(150, 1888, 333, 116); //results[0]["label"];
        if (!weifacheliang) {
            // toastLog("出错了，请检查微信内容");
            // throw "ttt";
            //ui.status.setText("出错了，请检查微信内容"); 没用
            break;
        }
        if (weifacheliang == "开始" || weifacheliang == "") {
            //toastLog("kais");
            break;
        }

        /* 截屏并获取包装图像对象. */
        // let imga = images.captureScreen();
        // let imgg = images.clip(imga, 150, 1888, 333, 116);
        /* 在区域 [ 0, 0, 100, 150 ] 内进行 OCR 识别并获取结果, 结果为字符串数组. */
        // let results = ocr(img, [159, 1893, 491, 2007]);

        // let useSlim = false;

        // CPU 线程数量, 实际好像没啥作用
        //  let cpuThreadNum = 8;

        //let imgg = images.read(imgg);
        //let results = ocr.paddle.detect(imgg, {
        //   useSlim,
        //   cpuThreadNum
        // });

        //toastLog(results[0]["label"]);
        // 回收图片
        //imgg.recycle();
        // break;

        /* 结果过滤, 筛选出文本中可部分匹配 "app" 的结果, 如 "apple", "disappear" 等. */
        // results.filter(text => text.includes('app'));

        //bounds(159, 1893, 491, 2007)
        //setClip("")                
        // toastLog("jjjjjj");
        sleep(150);
        press(332, 1950, 700); // 复制违法时间
        sleep(600);
        // toastLog("kkkk");
        click(540, 800);
        sleep(500);
        // let weifashijian = getClip();
        //weifashijian = weifashijian.split("：")[1].trim();
        //toast("剪贴板内容：" + weifashijian);

        // toastLog("The");
        // let weifacheliang = ocrr(150, 1888, 333, 116); //results[0]["label"];
        //setClip("");
        //longClick(445, 1738); // 复制违法车辆
        //enterX(328).centerY(1950)
        //centerX(329, 330).centerY(1950)

        //press(300, 1950, 800); //OCR代替复制
        //sleep(800);
        //  click(540, 800);
        // sleep(800);
        // weifacheliang = getClip();
        //getclip(weifacheliang);
        //toastLog(weifacheliang);
        //weifacheliang = weifacheliang.split("：")[1].trim();
        // toast("剪贴板内容：" + weifacheliang);
        //toastLog(!weifashijian || !weifacheliang);

        //centerX(384).centerY(1699)
        //centerX(383, 384).centerY(1720, 1721)
        // toastLog("xxxxx");
        click(384, 1721); // 保存图片到相册
        sleep(1900);
        //longClick(500, 1200);
        press(500, 1200, 800);
        sleep(100);
        click(500, 1230);
        sleep(200);

        back(); // 切到执法处理app

        // break;


        //sleep(800);
        recents();
        sleep(800);
        //swipe(226, 1300, 960, 1300, 800);
        //swipe(226, 1300, 960, 1300, 800);
        //sleep(1000);
        //click(560, 1111);
        if (i == 0) {
            click(80, 1200);
        } else {
            click(540, 1170);
        }
        sleep(600);

        yunduanban = ocrr(450, 128, 165, 75);


        if (yunduanban != "云端办") {
            break;
        }


        // setText(0, weifacheliang);
        click(740, 355); // 粘贴号牌搜索
        sleep(100);
        //press(930, 1270, 2000);
        //sleep(1000);
        // setClip(weifacheliang);
        // press(640, 355, 800);
        click(890, 348);
        sleep(100);

        press(740, 355, 800);
        sleep(300);
        click(261, 238);
        sleep(100);
        click(995, 352); // 点击搜索
        sleep(3000);

        //text('违法时间：2025-06-02 20:21:17
        //违法地点：新新大道民兵路路口（北往南）
        //违法行为：电动自行车未按照交通信号通行的（8072）
        //若该办理人（被处罚人）达到3宗及以上涉电动自行车的逾期未缴款记录，或者存在涉电动自行车的逾期未处理记录，请开具通知书E版；否则开具简易A版。
        //是否确认跳转开单？')
        // if (!textContains("是否确认跳转开单").exists()) {

        while (textContains('interrupted').exists()
            //  textContains("请稍后").exists() ||
        ) {
            click(540, 1298); // 确认
            sleep(100);
            click(995, 352); // 点击搜索
            sleep(2500);

        };
        while (textContains("请稍后").exists()) {
            click(560, 1280);
            sleep(100);
            click(995, 352); // 点击搜索
            sleep(2500);
        };
        while (textContains('interrupted').exists()) {
            click(540, 1298); // 确认
            sleep(100);
            click(995, 352); // 点击搜索
            sleep(2500);

        };
        while (textContains("请稍后").exists()) {
            click(560, 1280);
            sleep(100);
            click(995, 352); // 点击搜索
            sleep(2500);
        };


        while (textContains('unexpected end of').exists()) {
            click(530, 1360); // 确认
            sleep(100);
            click(995, 352); // 点击搜索
            sleep(2500);
        };

        if (textContains('未查询到数据').exists()) {
            click(540, 1298); // 确认
            sleep(400);
            // continue;
            // 下一步,去微信删除那三个
            weixinshanchu();
            continue;
        };

        // let yunduanbanchepai = ocrr(280, 280, 420, 120);
        // toastLog(yunduanbanchepai);
        // if (weifacheliang != yunduanbanchepai) {
        //   continue;
        // }


        // click(540,1443); // 点击逾期核查
        // click("逾期核查");
        //sleep(1200);
        // textContains("未缴款记录").waitFor();
        //centerX(540).centerY(1333, 1334

        //weijiaokuan = textContains("未查询到").exists()

        //sleep(800);
        // click(540, 1333);
        //   sleep(600);
        // textContains("未处理记录").waitFor();
        // sleep(600); //sleep(800);

        //weichuli = textContains("未查询到").exists();

        // sleep(800);
        // click(540, 1333);
        //   sleep(600);
        //id("btnOk").findOne().parent().click();
        //id("btnOk").findOne().parent().click();
        //id("btnOk").findOne().parent().waitFor();

        //click("开单"); //注意开单的位置会变化
        // centerX(880).centerY(1514, 1515)
        //click(880, 1515);

        // sleep(700);
        let firstBtn = id("btnKd").findOne();
        // sleep(900);
        firstBtn.click();
        sleep(300);

        //toast(weichuli);
        //if (weijiaokuan && weichuli) { // 简易A
        click("简易A版");
        //textContains("1年内的现场教育").waitFor();
        sleep(800);

        click(530, 1320);
        sleep(300);
        //click(540, 1333);
        //  sleep(300);
        if (textContains("请稍后").exists() ||
            textContains("未查询到当事人1年内的现场教育纠正记录！").exists()) {
            click(530, 1320);
            sleep(300);
        }


        // click("违法信息");
        click(810, 303);
        sleep(500);
        //click("选择");
        click(975, 1228);
        sleep(200);


        //setText(0, "018624"); //zhaoweiyan警号
        setText(0, selected);
        sleep(200);
        //centerX(979, 980).centerY(336, 337)
        click(980, 336); //搜索
        sleep(500);


        //centerX(540).centerY(490, 491)
        click(540, 490);
        sleep(500);

        // centerX(873).centerY(2091)
        //  click(873, 2091);
        //  sleep(100);
        //enterX(540).centerY(2151, 2152)
        click(540, 2152); // 打印预览
        sleep(500);


        //centerX(540).centerY(1297, 1298)
        click(540, 1298); //上传图片
        sleep(500);
        //centerX(194).centerY(1521)
        click(194, 1521);
        sleep(200);
        click("相册");
        sleep(1500);
        //centerX(259, 260).centerY(594, 595)
        click(260, 595);
        sleep(100);
        //centerX(990).centerY(165)
        click(990, 165);
        sleep(400);
        click(540, 2152); // 打印预览
        sleep(2000);


        while (textContains("请稍后").exists()) {
            click(550, 1300);
            sleep(200);
            // 取消业务数据校验停住的问题
            while (textContains("已取消业务数据校验").exists()) {
                click(290, 1300); //是
                //click(773, 1334); //否
                sleep(800);
                click(540, 2152); // 打印预览
                sleep(2000);
            }
        }

        while (textContains("已取消业务数据校验").exists()) {
            click(290, 1300); //是
            //click(773, 1334); //否
            sleep(800);
            click(540, 2152); // 打印预览
            sleep(2000);
        }

        if (textContains("请选择骑手性质").exists()) {
            click(540, 1298);
            sleep(600);
            click(810, 1860);
            sleep(600);
            click("众包");
            sleep(800);
            click(540, 2158); // 打印
            sleep(600);
        }




        if (textContains("继续开单").exists()) {
            break;
            //  centerX(772, 773).centerY(1544, 1545)
            click(773, 1545); // 否
            sleep(800);
            //centerX(772, 773).centerY(1400, 1401)
            // centerX(307, 308).centerY(1400, 1401)
            click(773, 1400); //否
            sleep(600);

            back();
            sleep(800);
            // centerX(307, 308).centerY(1297, 1298)
            click(308, 1298); // 是        
            sleep(800);

            weixinshanchu(weifacheliang);
            continue;

        }

        if (textContains("该违法符合教育纠正条件").exists()) {
            // text('民警当日开具的简易程序已达131起（上限150起）
            //注意：该违法符合教育纠正条件，当事人及时改正且没有造成危害后果的，可以教育纠正。
            //是否将执法方式调整为教育纠正？
            //【是】改为教育纠正，【否】继续进行处罚。')
            if (textContains("民警当日开具的简易程序已达").exists()) {
                break;
            }
            //centerX(307, 308).centerY(1477, 1478)
            //click(308, 1478); //是
            //centerX(772, 773).centerY(1477, 1478) //否
            click(773, 1478);
            sleep(700);
            //  click(773, 1478);
            // sleep(800); // 解决教育纠正突然停止，需要手动点的问题
            //   click(540, 2158); // 打印
            //  sleep(2000);

            click(540, 2158); // 打印


            dangchangchufayulann = ocrr(324, 128, 502, 73);
            if (dangchangchufayulann != "当场处罚打印预览") {
                break;
            }
            
            sleep(800);
            // click("是");//centerX(307, 308).centerY(1297, 1298)
            click(308, 1298);

            sleep(1000);
            click("确定");
            sleep(800); // 会返回输入车牌号码界面



            weixinshanchu(weifacheliang);

            continue;
            //enterX(540).centerY(2151, 2152)
            //sleep(800);
            //  click(620, 413); //教育方式
            //sleep(800);
            //centerX(540).centerY(1034, 1035)
            //  click(540, 1035); // 12123
            //  sleep(800);
            //  click(540, 2152); // 原打印位置
            // sleep(800);
            //  click(308, 1868); // 这个是的位置不一样
            // sleep(3000);
            //centerX(540).centerY(1297, 1298)
            //  click(540, 1298);
            //sleep(1500);
            // continue;
            //centerX(307, 308).centerY(1868, 1869)
            //enterX(619, 620).centerY(413, 414)
        }

        // text('该违法符合首违警告情形，适用警告处罚。
        //是否继续？ ')

        if (textContains("违法符合首违警告情形").exists()) {
            click(773, 1370);
            sleep(600);
            click("警告");
            sleep(900);
            click(540, 2152); // 打印预览
            sleep(2000);

        }


        if (textContains("民警当日开具的简易程序已达").exists()) {
            break;
        }


        // sleep(1100);
        // textContains('当场处罚打印预览').waitFor(); //可能解决突然停止的问题
        // bounds(324, 128, 756, 201)


        //click("打印");
        click(540, 2158); // 打印
        dangchangchufayulan = ocrr(324, 128, 502, 73);
        if (dangchangchufayulan != "当场处罚打印预览") {
            break;
        }
        sleep(800);
        // click("是");//centerX(307, 308).centerY(1297, 1298)
        click(308, 1298);

        sleep(1000);
        click("确定");
        sleep(1000); // 会返回输入车牌号码界面



        // } else {
        //     toastLog('[通知书E版]正在开发中，请联系未来中心负责人');

        // }
        weixinshanchu(weifacheliang);




    }
}

function weixinshanchu(weifacheliang) {
    // 下一步,去微信删除那三个
    launchApp("政务微信");
    sleep(500);
    //centerX(516).centerY(1924, 1925)
    press(330, 1950, 800);
    sleep(200);

    // centerX(540).centerY(1516, 1517)
    click(540, 1371); // 多选
    sleep(200);

    //enterX(487, 488).centerY(1721, 1722)
    click(488, 1722);
    sleep(200);


    //centerX(944, 945).centerY(2145)
    click(945, 2145);
    sleep(600);
    //click("确认");
    //enterX(834).centerY(1251)
    click(834, 1251);
    sleep(300);
    chepaiResults.push(weifacheliang);

}

function weixinshanchu() {

    launchApp("政务微信");
    sleep(500);
    //centerX(516).centerY(1924, 1925)
    press(330, 1950, 800);
    sleep(200);

    // centerX(540).centerY(1516, 1517)
    click(540, 1371); // 多选
    sleep(200);

    //enterX(487, 488).centerY(1721, 1722)
    click(488, 1722);
    sleep(200);


    //centerX(944, 945).centerY(2145)
    click(945, 2145);
    sleep(600);
    //click("确认");
    //enterX(834).centerY(1251)
    click(834, 1251);
    sleep(200);
    // chepaiResults.push(weifacheliang);

}

function getclip(weifacheliang) {
    var window = floaty.window(
        <frame visibility="invisible">
            <input id="input"/>
        </frame>);
    ui.run(function() {
        window.requestFocus();
        window.input.requestFocus();
        weifacheliang = getClip();
        toastLog(weifacheliang);
        window.close();
        // return getClip();
    });
}

function ocrr(x, y, w, h) {
    /* 截屏并获取包装图像对象. */
    let imga = images.captureScreen();
    // let img = images.clip(imga, 150, 1888, 333, 116);
    let img = images.clip(imga, x, y, w, h);
    /* 在区域 [ 0, 0, 100, 150 ] 内进行 OCR 识别并获取结果, 结果为字符串数组. */
    // let results = ocr(img, [159, 1893, 491, 2007]);

    let useSlim = false;

    // CPU 线程数量, 实际好像没啥作用
    let cpuThreadNum = 8;

    //  let imgg = images.read(img);
    let results = ocr.paddle.detect(img, {
        useSlim,
        cpuThreadNum
    });

    toastLog(results[0]["label"]);
    // 回收图片
    img.recycle();
    imga.recycle();
    // break;

    return results[0]["label"]
}