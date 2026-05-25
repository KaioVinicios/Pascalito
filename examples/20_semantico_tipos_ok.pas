program semanticook;
var
  contador, total: integer;
  feito: boolean;
  msg: string;
begin
  contador := 0;
  total := 100;
  feito := false;
  read(msg);
  while ~feito do
  begin
    contador := contador + 1;
    if contador >= total then
      feito := true
    else
      write("ainda nao", contador, msg)
  end;
  write("fim", contador)
end.
