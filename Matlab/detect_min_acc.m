function [isMin] = detect_min_acc(acc,acc_m1)
acc_thold=-2;
if(acc<acc_thold)
  if(acc_m1<acc_thold)
    if(acc>acc_m1)
      isMin=true;
    else
      isMin=false;
    end
  else
    isMin=false;
  end
else
  if(acc_m1<acc_thold)
    isMin=true;
  else
    isMin=false;
  end
end
end