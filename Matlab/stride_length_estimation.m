%% Clear History
% close all
% clc
clear int_x acc_max acc_mean acc_min acc_var gyr_max gyr_mean gyr_min gyr_var stride_frequency temp endHeading

%% Initializing
acc = raw(:,1:3);
gyr = raw(:,4:6);
mag = raw(:,7:9);
tem = raw(:,10);
% baro=Untitled(:,11)
% target=86.5;

buff_mag_length=zeros(1,3);
buff_acc=zeros(1,3);
buff_gyr=zeros(1,3);
buff_tem=0;
acc_x_max_thold=2;
acc_x_min_thold=-2;
acc_m1=zeros(1,3);
process=0;
start_gyr_thold=2;
stride_count=0;
stride_length=0;
buff_stride_frequency=0;
iEndBuffHeading=15;

%% Computing Length
for i=1:length(acc)
  if(process==0)
    isStart = detect_start_stride(gyr(i,3));
    if(isStart)
      process = 1;
      buff_stride_frequency=buff_stride_frequency+1;
      buff_acc(buff_stride_frequency,1:3)=acc_m1;
      buff_gyr(buff_stride_frequency,1:3)=gyr_m1;
      buff_tem = buff_tem+tem(i);
      
    end
  elseif(process==1)
    buff_stride_frequency=buff_stride_frequency+1;
    buff_acc(buff_stride_frequency,1:3)=acc_m1;
    buff_gyr(buff_stride_frequency,1:3)=gyr_m1;
    buff_tem = buff_tem+tem(i);
    
    isMax=detect_max_acc(acc(i,1),acc_m1(1));
    if(isMax)
      process=2;
    end
  elseif(process==2)
    buff_stride_frequency=buff_stride_frequency+1;
    buff_acc(buff_stride_frequency,1:3)=acc_m1;
    buff_gyr(buff_stride_frequency,1:3)=gyr_m1;
    buff_tem = buff_tem+tem(i);
    
    isMin=detect_min_acc(acc(i,1),acc_m1(1));
    if(isMin)
      process=3;
    end
  elseif(process==3)
    buff_stride_frequency=buff_stride_frequency+1;
    buff_acc(buff_stride_frequency,1:3)=acc_m1;
    buff_gyr(buff_stride_frequency,1:3)=gyr_m1;
    buff_tem = buff_tem+tem(i);
    
    isStop = detect_stop_stride(gyr(i,3), gyr_m1(3));
    if(isStop)
      process=4; %ANN
    end
  elseif(process==4)
    buffEndHeading=0;
    while(iEndBuffHeading>0)
      roll = atan2(acc(i,3),acc(i,2));
      pitch = atan2(acc(i,1),sqrt((acc(i,2)*acc(i,2))+(acc(i,3)*acc(i,3))));
      magX = mag(i,1)*cos(pitch) + mag(i,2)*sin(pitch);
      magY = mag(i,1)*sin(roll)*sin(pitch) + mag(i,3)*cos(roll) - mag(i,2)*sin(roll)*cos(pitch);
      heading = rad2deg(atan2(magY,magX));
      if(heading<0)
        heading = heading + 360;
      end
      buffEndHeading = buffEndHeading+heading;
      iEndBuffHeading=iEndBuffHeading-1;
    end
    stride_count=stride_count+1;
%         x(1) = 0;
%         v(1) = 0;
%         for i=2:length(buff_acc)
%             v(i) = v(i-1) + buff_acc(i-1,1)*0.04;
%             x(i) = x(i-1) + v(i-1)*0.04;
%         end
%         int_x(stride_count,:)=x(length(buff_acc))*-100;
    acc_max(stride_count,:)=max(buff_acc);
    acc_min(stride_count,:)=min(buff_acc);
    gyr_max(stride_count,:)=max(buff_gyr);
    gyr_min(stride_count,:)=min(buff_gyr);
    acc_mean(stride_count,:)=mean(buff_acc);
    acc_var(stride_count,1:3)=var(buff_acc);
    gyr_mean(stride_count,:)=mean(buff_gyr);
    gyr_var(stride_count,:)=var(buff_gyr);
    stride_frequency(stride_count,1)=buff_stride_frequency;
    temp(stride_count,1)=buff_tem/buff_stride_frequency;
    endHeading(stride_count,1)=buffEndHeading/15;
    
    
    process=5;
    clear buff_acc buff_gyr;
  end
  if(process==5)
    % Forwards Pass
%     in_raw = [acc_max(stride_count,:) acc_min(stride_count,:) acc_mean(stride_count,:) acc_var(stride_count,:) ...
%       gyr_max(stride_count,:) gyr_min(stride_count,:) gyr_mean(stride_count,:) gyr_var(stride_count,:) ...
%       stride_frequency(stride_count) temp(stride_count)];
%     in = ((in_raw-mean_processed')./std_processed')';
%     net_h1 = bih1 + wih1*in;
% %     out_h1 = 1./(1+exp(-net_h1));
%     out_h1 = max(0,net_h1);
%     net_o = bo + wo'*out_h1;
%     out_o = net_o
% %     se = se + (out_o-(processed(stride_count,29)/100))^2;
%     stride_length=stride_length+out_o;

%     in_raw = [acc_max(stride_count,1)];
%     in = ((in_raw-mean_processed)./std_processed)';
%     net_h1 = bih1 + wih1*in;
%     out_h1 = 1./(1+exp(-net_h1));
%     net_o = bo + wo'*out_h1;
%     out_o = (net_o*std_t)+mean_t
%     stride_length=stride_length+out_o;

    buff_tem = 0;
    process=0;
    buff_stride_frequency=0;
    iEndBuffHeading=15;
  end
  acc_m1=acc(i,:);
  gyr_m1=gyr(i,:);
end

%% Print Result
% stride_count
% stride_length

% % Leader
% close all
% xref(1) = 0;
% yref(1) = 0;
% for i=1:length(processed(:,1))
%   dxref(i) = cosd(processed(i,3))*processed(i,5);
%   dyref(i) = sind(processed(i,3))*processed(i,5);
%   xref(i+1) = xref(i) + dxref(i);
%   yref(i+1) = yref(i) + dyref(i);
% end
% plot(-yref,xref,'LineWidth',1.5)
% hold on
% x(1) = 0;
% y(1) = 0;
% for i=1:length(processed(:,1))
%   dx(i) = cosd(processed(i,1))*processed(i,2);
%   dy(i) = sind(processed(i,1))*processed(i,2);
%   x(i+1) = x(i) + dx(i);
%   y(i+1) = y(i) + dy(i);
% end
% plot(-y,x,'--','LineWidth',1.5)
% legend('Reference', 'Estimation')
% title('Leader Navigation System Testing')
% xlabel('y [cm]')
% ylabel('x [cm]')

% % Follower
% close all
% xref = test(:,1);
% yref = test(:,2);
% dxref = test(:,3);
% dyref = test(:,4);
% plot(yref,xref,'LineWidth',1.5)
% hold on
% x = test(:,6);
% y = test(:,7);
% dx = test(:,8);
% dy = test(:,9);
% plot(y,x,'--','LineWidth',1.5)
% legend('Reference', 'Estimation')
% title('Follower Navigation System Testing')
% xlabel('y [cm]')
% ylabel('x [cm]')
% headref = test(:,11);
% slref = test(:,12);
% head = test(:,13);
% sl = test(:,14);

% % Leader-Follower
close all
headref = lfbaru(:,1);
slref = lfbaru(:,2);
xref(1)=0;
yref(1)=0;
for i=1:length(headref)
  dxref(i) = cosd(headref(i))*slref(i);
  dyref(i) = sind(headref(i))*slref(i);
  xref(i+1) = xref(i) + dxref(i);
  yref(i+1) = yref(i) + dyref(i);
end

headlead = lfbaru(:,4);
sllead = lfbaru(:,5);
xlead(1)=0;
ylead(1)=0;
for i=1:length(headlead)
  dxlead(i) = cosd(headlead(i))*sllead(i);
  dylead(i) = sind(headlead(i))*sllead(i);
  xlead(i+1) = xlead(i) + dxlead(i);
  ylead(i+1) = ylead(i) + dylead(i);
end

xfol = lfbaru(:,7);
yfol = lfbaru(:,8);
dxfol = lfbaru(:,9);
dyfol = lfbaru(:,10);
headfol = lfbaru(:,11);
slfol = lfbaru(:,12);


plot(-yref,xref,'LineWidth',1.5)
hold on
plot(-ylead,xlead,'--','LineWidth',1.5)
plot(-yfol,xfol,'--','LineWidth',1.5)
legend('Reference', 'Leader', 'Follower')
title('Leader-Follower Navigation System Testing')
xlabel('y [cm]')
ylabel('x [cm]')