function isStart = detect_start_stride(gyr)
if(abs(gyr)>2)
  isStart=true;
else
  isStart=false;
end
end
