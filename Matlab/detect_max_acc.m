function isMax = detect_max_acc(acc,acc_m1)
acc_thold = 2;
if(acc>acc_thold)
  if(acc_m1>acc_thold)
    if(acc<acc_m1)
      isMax=true;
    else
      isMax=false;
    end
  else
    isMax=false;
  end
else
  if(acc_m1>acc_thold)
    isMax=true;
  else
    isMax=false;
  end
end
end