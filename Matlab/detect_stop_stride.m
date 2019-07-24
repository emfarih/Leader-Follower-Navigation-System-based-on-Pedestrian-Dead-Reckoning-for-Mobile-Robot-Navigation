function [isStop] = detect_stop_stride(gyr, gyr_m1)
if(abs(gyr)<0.1 && abs(gyr_m1)<0.1)
  isStop=true;
else
  isStop=false;
end
end
